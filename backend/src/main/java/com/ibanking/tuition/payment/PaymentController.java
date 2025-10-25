package com.ibanking.tuition.payment;

import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import com.ibanking.tuition.tuition.SemesterUtil;
import com.ibanking.tuition.tuition.StudentTuition;
import com.ibanking.tuition.tuition.StudentTuitionRepository;
import com.ibanking.tuition.email.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final CustomerRepository customerRepository;
    private final StudentTuitionRepository studentTuitionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private final int otpTtlSeconds;
    private final int otpLength;
    private final int maxAttempts;

    public PaymentController(CustomerRepository customerRepository, 
                           StudentTuitionRepository studentTuitionRepository, 
                           PaymentTransactionRepository paymentTransactionRepository,
                           PaymentService paymentService,
                           StringRedisTemplate redisTemplate, 
                           JavaMailSender mailSender,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           @org.springframework.beans.factory.annotation.Value("${app.otp.ttlSeconds}") int otpTtlSeconds, 
                           @org.springframework.beans.factory.annotation.Value("${app.otp.length}") int otpLength, 
                           @org.springframework.beans.factory.annotation.Value("${app.otp.maxAttempts}") int maxAttempts) {
        this.customerRepository = customerRepository;
        this.studentTuitionRepository = studentTuitionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentService = paymentService;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.otpTtlSeconds = otpTtlSeconds;
        this.otpLength = otpLength;
        this.maxAttempts = maxAttempts;
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(Authentication auth, @Valid @RequestBody InitiateRequest req) {
        Customer payer = customerRepository.findByUsername(auth.getName()).orElseThrow();
        String currentSemester = SemesterUtil.currentSemester();
        String normalized = req.studentId().trim().toUpperCase();
        
        // Use PaymentService for better concurrency control
        String payerLockKey = lockKey("payer", String.valueOf(payer.getId()));
        String tuitionLockKey = lockKey("tuition", normalized + ":" + currentSemester);
        
        boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
        boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);
        
        if (!(payerLocked && tuitionLocked)) {
            if (payerLocked) paymentService.releaseLock(payerLockKey);
            if (tuitionLocked) paymentService.releaseLock(tuitionLockKey);
            return ResponseEntity.status(423).body(Map.of("message", "Resource busy, please try again later"));
        }
        
        try {
            // Check if tuition is still available
            if (!paymentService.isTuitionAvailable(normalized, currentSemester)) {
                return ResponseEntity.status(404).body(Map.of("message", "No unpaid tuition for current semester"));
            }
            
            // Check if customer has sufficient balance
            StudentTuition t = studentTuitionRepository.findByStudentIdAndSemesterAndPaidIsFalse(normalized, currentSemester)
                    .orElse(null);
            if (t == null) {
                return ResponseEntity.status(404).body(Map.of("message", "No unpaid tuition for current semester"));
            }
            
            if (!paymentService.hasSufficientBalance(payer.getId(), t.getAmount())) {
                return ResponseEntity.status(400).body(Map.of("message", "Insufficient balance"));
            }

            PaymentTransaction txn = new PaymentTransaction();
            txn.setPayerCustomerId(payer.getId());
            txn.setStudentId(t.getStudentId());
            txn.setSemester(t.getSemester());
            txn.setAmount(t.getAmount());
            txn.setStatus(PaymentTransaction.Status.PENDING_OTP);
            txn.setCreatedAt(OffsetDateTime.now());
            txn.setLockId(UUID.randomUUID().toString());
            txn.setLockExpiry(OffsetDateTime.now().plusSeconds(30));
            txn = paymentTransactionRepository.save(txn);

            String otp = generateOtp(otpLength);
            String otpKey = otpKey(txn.getId());
            String attemptKey = attemptKey(txn.getId());
            redisTemplate.opsForValue().set(otpKey, otp, Duration.ofSeconds(otpTtlSeconds));
            redisTemplate.opsForValue().setIfAbsent(attemptKey, "0", Duration.ofSeconds(otpTtlSeconds));

            // Send OTP email
            emailService.sendOtpEmail(payer, otp, txn, t);

            return ResponseEntity.ok(Map.of("transactionId", txn.getId(), "ttlSeconds", otpTtlSeconds));
            
        } finally {
            // Always release locks
            paymentService.releaseLock(payerLockKey);
            paymentService.releaseLock(tuitionLockKey);
        }
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirm(Authentication auth, @Valid @RequestBody ConfirmRequest req) {
        PaymentTransaction txn = paymentTransactionRepository.findById(req.transactionId()).orElse(null);
        if (txn == null || txn.getStatus() != PaymentTransaction.Status.PENDING_OTP) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid transaction"));
        }
        
        String otpKey = otpKey(txn.getId());
        String attemptKey = attemptKey(txn.getId());
        String expected = redisTemplate.opsForValue().get(otpKey);
        if (expected == null) {
            // Mark transaction as failed if OTP is not found
            txn.setStatus(PaymentTransaction.Status.FAILED);
            txn.setCompletedAt(OffsetDateTime.now());
            paymentTransactionRepository.save(txn);
            return ResponseEntity.status(400).body(Map.of("message", "OTP expired. Transaction failed."));
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(otpKey);
            return ResponseEntity.status(429).body(Map.of("message", "Too many attempts"));
        }

        if (!expected.equals(req.otp())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP"));
        }

        // Use PaymentService for processing with proper concurrency control
        try {
            boolean success = paymentService.processPayment(txn.getId());
            if (success) {
                // Send confirmation email
                Customer payer = customerRepository.findById(txn.getPayerCustomerId()).orElseThrow();
                StudentTuition tuition = studentTuitionRepository.findByStudentIdAndSemester(txn.getStudentId(), txn.getSemester()).orElseThrow();
                sendConfirmationEmail(payer, txn, tuition);
                
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptKey);
                
                // Return detailed transaction information for success popup
                return ResponseEntity.ok(Map.of(
                    "message", "Payment successful",
                    "transactionId", txn.getId(),
                    "studentId", txn.getStudentId(),
                    "semester", txn.getSemester(),
                    "amount", txn.getAmount(),
                    "studentName", tuition.getStudentName(),
                    "payerName", payer.getFullName(),
                    "completedAt", txn.getCompletedAt() != null ? txn.getCompletedAt().toString() : OffsetDateTime.now().toString()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of("message", "Payment processing failed"));
            }
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Concurrent modification")) {
                return ResponseEntity.status(409).body(Map.of("message", "Transaction conflict detected, please retry"));
            }
            return ResponseEntity.status(500).body(Map.of("message", "Payment processing failed: " + e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(Authentication auth, @Valid @RequestBody ResendOtpRequest req) {
        PaymentTransaction txn = paymentTransactionRepository.findById(req.transactionId()).orElse(null);
        if (txn == null || (txn.getStatus() != PaymentTransaction.Status.PENDING_OTP && txn.getStatus() != PaymentTransaction.Status.EXPIRED)) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid transaction status for resend"));
        }
        
        Customer payer = customerRepository.findById(txn.getPayerCustomerId()).orElseThrow();
        if (!payer.getUsername().equals(auth.getName())) {
            return ResponseEntity.status(403).body(Map.of("message", "Unauthorized"));
        }

        // Generate new OTP
        String otp = generateOtp(otpLength);
        String otpKey = otpKey(txn.getId());
        String attemptKey = attemptKey(txn.getId());
        
        // Reset OTP and attempts
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptKey);
        redisTemplate.opsForValue().set(otpKey, otp, Duration.ofSeconds(otpTtlSeconds));
        redisTemplate.opsForValue().setIfAbsent(attemptKey, "0", Duration.ofSeconds(otpTtlSeconds));
        
        // Reset transaction status to PENDING_OTP if it was EXPIRED
        if (txn.getStatus() == PaymentTransaction.Status.EXPIRED) {
            txn.setStatus(PaymentTransaction.Status.PENDING_OTP);
            paymentTransactionRepository.save(txn);
        }

        // Send new OTP email
        emailService.sendOtpEmail(payer, otp, txn, studentTuitionRepository.findByStudentIdAndSemester(txn.getStudentId(), txn.getSemester()).orElseThrow());

        return ResponseEntity.ok(Map.of("message", "New OTP sent", "ttlSeconds", otpTtlSeconds));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(Authentication auth) {
        // Clean up expired transactions before returning history
        paymentService.processExpiredOtpTransactions();
        
        Customer payer = customerRepository.findByUsername(auth.getName()).orElseThrow();
        var list = paymentTransactionRepository.findByPayerCustomerIdOrderByCreatedAtDesc(payer.getId());
        return ResponseEntity.ok(list.stream().map(txn -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", txn.getId());
            m.put("studentId", txn.getStudentId());
            m.put("semester", txn.getSemester());
            m.put("amount", txn.getAmount());
            m.put("status", txn.getStatus() != null ? txn.getStatus().name() : null);
            m.put("createdAt", txn.getCreatedAt());
            m.put("completedAt", txn.getCompletedAt());
            return m;
        }).toList());
    }

    @PostMapping("/cleanup-expired")
    public ResponseEntity<?> cleanupExpired() {
        try {
            paymentService.processExpiredOtpTransactions();
            return ResponseEntity.ok(Map.of("message", "Cleanup completed"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Cleanup failed: " + e.getMessage()));
        }
    }

    @PostMapping("/seed-students")
    public ResponseEntity<?> seedStudents() {
        try {
            int createdCount = 0;
            for (int i = 111; i <= 123; i++) {
                String suffix = String.format("%04d", i);
                String mssv = "523H" + suffix;
                
                if (customerRepository.findByUsername(mssv).isPresent()) {
                    continue;
                }

                Customer c = new Customer();
                c.setUsername(mssv);
                c.setPasswordHash(passwordEncoder.encode("pass123"));
                c.setFullName(generateVietnameseName(i));
                c.setBalance(new java.math.BigDecimal("15000000"));
                c.setPhone(generatePhone(i));
                c.setEmail("iannwendii@gmail.com");
                customerRepository.save(c);
                createdCount++;
            }
            
            return ResponseEntity.ok(Map.of("message", "Created " + createdCount + " new students", "createdCount", createdCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error creating students: " + e.getMessage()));
        }
    }

    // Helper methods for email sending

    private void sendConfirmationEmail(Customer payer, PaymentTransaction txn, StudentTuition tuition) {
        try {
            emailService.sendPaymentConfirmationEmail(payer, txn, tuition);
        } catch (Exception e) {
            System.err.println("Failed to send confirmation email: " + e.getMessage());
        }
    }


    private String generateOtp(int len) {
        String digits = "0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(digits.charAt(r.nextInt(digits.length())));
        return sb.toString();
    }

    private String otpKey(Long txnId) { return "otp:txn:" + txnId; }
    private String attemptKey(Long txnId) { return "otp:attempt:" + txnId; }
    private String lockKey(String type, String id) { return "lock:" + type + ":" + id; }

    private static String generatePhone(int idx) {
        String base = String.format("%08d", idx);
        return "090" + base.substring(base.length() - 8);
    }

    private static final String[] LAST_NAMES = new String[]{
            "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng",
            "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"
    };
    private static final String[] MID_NAMES = new String[]{
            "Thị", "Văn", "Hữu", "Ngọc", "Anh", "Quốc", "Gia", "Bảo", "Minh", "Thanh"
    };
    private static final String[] GIVEN_NAMES = new String[]{
            "An", "Bình", "Châu", "Dũng", "Đạt", "Giang", "Hà", "Hạnh", "Hằng", "Hiếu",
            "Huy", "Hương", "Khanh", "Khánh", "Lan", "Linh", "Long", "Minh", "My", "Nam",
            "Ngân", "Ngọc", "Nhi", "Phong", "Phúc", "Quân", "Quang", "Quyên", "Sơn", "Tâm",
            "Tân", "Thảo", "Thắng", "Thịnh", "Thu", "Trang", "Trung", "Tuấn", "Tú", "Tùng",
            "Uyên", "Vy", "Yến"
    };

    private static String generateVietnameseName(int seed) {
        String last = LAST_NAMES[seed % LAST_NAMES.length];
        String mid = MID_NAMES[(seed / 3) % MID_NAMES.length];
        String given = GIVEN_NAMES[(seed / 7) % GIVEN_NAMES.length];
        return last + " " + mid + " " + given;
    }

    public record InitiateRequest(@NotBlank @Pattern(regexp = "^.{8}$") String studentId) {}
    public record ConfirmRequest(Long transactionId, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {}
    public record ResendOtpRequest(Long transactionId) {}
}


