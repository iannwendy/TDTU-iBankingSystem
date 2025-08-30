package com.ibanking.tuition.payment;

import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import com.ibanking.tuition.tuition.SemesterUtil;
import com.ibanking.tuition.tuition.StudentTuition;
import com.ibanking.tuition.tuition.StudentTuitionRepository;
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

    private final int otpTtlSeconds;
    private final int otpLength;
    private final int maxAttempts;

    public PaymentController(CustomerRepository customerRepository, 
                           StudentTuitionRepository studentTuitionRepository, 
                           PaymentTransactionRepository paymentTransactionRepository,
                           PaymentService paymentService,
                           StringRedisTemplate redisTemplate, 
                           JavaMailSender mailSender, 
                           @org.springframework.beans.factory.annotation.Value("${app.otp.ttlSeconds}") int otpTtlSeconds, 
                           @org.springframework.beans.factory.annotation.Value("${app.otp.length}") int otpLength, 
                           @org.springframework.beans.factory.annotation.Value("${app.otp.maxAttempts}") int maxAttempts) {
        this.customerRepository = customerRepository;
        this.studentTuitionRepository = studentTuitionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentService = paymentService;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
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
            sendOtpEmail(payer, otp, txn, t);

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
            // Mark transaction as expired if OTP is not found
            txn.setStatus(PaymentTransaction.Status.EXPIRED);
            paymentTransactionRepository.save(txn);
            return ResponseEntity.status(400).body(Map.of("message", "OTP expired. Please request a new one."));
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
                
                return ResponseEntity.ok(Map.of("message", "Payment successful"));
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
        sendNewOtpEmail(payer, otp, txn);

        return ResponseEntity.ok(Map.of("message", "New OTP sent", "ttlSeconds", otpTtlSeconds));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(Authentication auth) {
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

    // Helper methods for email sending
    private void sendOtpEmail(Customer payer, String otp, PaymentTransaction txn, StudentTuition tuition) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("no-reply@ibanking.local");
            helper.setTo(payer.getEmail());
            helper.setSubject("iBanking Tuition Payment – OTP Verification");
            String amountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
            String html = """
                <div style='background:#0b1020;padding:24px;font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#e5e7eb;'>
                  <div style='max-width:640px;margin:0 auto;background:#11162a;border:1px solid rgba(255,255,255,.08);border-radius:16px;overflow:hidden;'>
                    <div style='padding:20px 24px;border-bottom:1px solid rgba(255,255,255,.08);display:flex;align-items:center;gap:10px;'>
                      <span style='display:inline-block;width:10px;height:10px;background:#3b82f6;border-radius:50%'></span>
                      <strong style='font-size:16px;color:#fff'>iBanking Tuition Payment</strong>
                    </div>
                    <div style='padding:24px;color:#d1d5db;'>
                      <p style='margin:0 0 12px 0;'>Hi <strong style='color:#fff'>%s</strong>,</p>
                      <p style='margin:0 0 16px 0;'>Use the One-Time Password (OTP) below to confirm your tuition payment.</p>
                      <div style='margin:16px 0 20px 0;padding:14px 18px;background:#0f172a;border:1px solid rgba(255,255,255,.08);border-radius:12px;text-align:center;'>
                        <div style='font-size:13px;letter-spacing:.02em;color:#94a3b8;margin-bottom:6px;'>Your OTP</div>
                        <div style='font-size:28px;letter-spacing:.3em;color:#fff;font-weight:700'>%s</div>
                      </div>
                      <table style='width:100%%;font-size:14px;color:#d1d5db;margin:12px 0 20px 0;'>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Transaction ID</td><td style='text-align:right;color:#fff'>%d</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Student ID</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Semester</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Amount</td><td style='text-align:right;color:#fff'>%s</td></tr>
                      </table>
                      <p style='margin:0 0 8px 0;font-size:12px;color:#94a3b8;'>Do not share this OTP with anyone. It will expire in %d seconds.</p>
                      <p style='margin:0 0 0 0;font-size:12px;color:#94a3b8;'>If you didn't request this, please ignore this email.</p>
                    </div>
                  </div>
                  <div style='max-width:640px;margin:12px auto 0 auto;text-align:center;font-size:12px;color:#8b93a7;'>
                    © %d iBanking. All rights reserved.
                  </div>
                </div>
            """.formatted(
                    payer.getFullName(),
                    otp,
                    txn.getId(),
                    tuition.getStudentId(),
                    tuition.getSemester(),
                    amountStr,
                    otpTtlSeconds,
                    java.time.LocalDate.now().getYear()
            );
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            System.err.println("[MAIL] Failed to send HTML email, reason: " + e.getMessage());
            try {
                var fallback = mailSender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(fallback, false, "UTF-8");
                h.setFrom("no-reply@ibanking.local");
                h.setTo(payer.getEmail());
                h.setSubject("Your OTP for tuition payment");
                h.setText("Your OTP is: " + otp + "\nTransaction ID: " + txn.getId());
                mailSender.send(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void sendConfirmationEmail(Customer payer, PaymentTransaction txn, StudentTuition tuition) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("no-reply@ibanking.local");
            helper.setTo(payer.getEmail());
            helper.setSubject("iBanking Tuition Payment – Transaction Confirmed");
            String amountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
            String html = """
                <div style='background:#0b1020;padding:24px;font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#e5e7eb;'>
                  <div style='max-width:640px;margin:0 auto;background:#11162a;border:1px solid rgba(255,255,255,.08);border-radius:16px;overflow:hidden;'>
                    <div style='padding:20px 24px;border-bottom:1px solid rgba(255,255,255,.08);display:flex;align-items:center;gap:10px;'>
                      <span style='display:inline-block;width:10px;height:10px;background:#10b981;border-radius:50%'></span>
                      <strong style='font-size:16px;color:#fff'>Payment Confirmed</strong>
                    </div>
                    <div style='padding:24px;color:#d1d5db;'>
                      <p style='margin:0 0 12px 0;'>Hi <strong style='color:#fff'>%s</strong>,</p>
                      <p style='margin:0 0 16px 0;'>Your tuition payment has been successfully processed.</p>
                      <div style='margin:16px 0 20px 0;padding:14px 18px;background:#0f172a;border:1px solid rgba(255,255,255,.08);border-radius:12px;text-align:center;'>
                        <div style='font-size:13px;letter-spacing:.02em;color:#94a3b8;margin-bottom:6px;'>Transaction Status</div>
                        <div style='font-size:28px;letter-spacing:.1em;color:#10b981;font-weight:700'>SUCCESS</div>
                      </div>
                      <table style='width:100%%;font-size:14px;color:#d1d5db;margin:12px 0 20px 0;'>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Transaction ID</td><td style='text-align:right;color:#fff'>%d</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Student ID</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Semester</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Amount Paid</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Payment Date</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Remaining Balance</td><td style='text-align:right;color:#fff'>%s</td></tr>
                      </table>
                      <p style='margin:0 0 8px 0;font-size:12px;color:#94a3b8;'>Thank you for using iBanking services.</p>
                      <p style='margin:0 0 0 0;font-size:12px;color:#94a3b8;'>If you have any questions, please contact our support team.</p>
                    </div>
                  </div>
                  <div style='max-width:640px;margin:12px auto 0 auto;text-align:center;font-size:12px;color:#8b93a7;'>
                    © %d iBanking. All rights reserved.
                  </div>
                </div>
            """.formatted(
                    payer.getFullName(),
                    txn.getId(),
                    tuition.getStudentId(),
                    tuition.getSemester(),
                    amountStr,
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    String.format("%,.0f VND", payer.getBalance().doubleValue()),
                    java.time.LocalDate.now().getYear()
            );
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            System.err.println("[MAIL] Failed to send confirmation email, reason: " + e.getMessage());
            try {
                var fallback = mailSender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(fallback, false, "UTF-8");
                h.setFrom("no-reply@ibanking.local");
                h.setTo(payer.getEmail());
                h.setSubject("Payment Confirmed - Transaction ID: " + txn.getId());
                String fallbackAmountStr = String.format("%,.0f VND", tuition.getAmount().doubleValue());
                h.setText("Your tuition payment of " + fallbackAmountStr + " has been successfully processed.\nTransaction ID: " + txn.getId() + "\nStudent ID: " + tuition.getStudentId() + "\nSemester: " + tuition.getSemester());
                mailSender.send(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void sendNewOtpEmail(Customer payer, String otp, PaymentTransaction txn) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom("no-reply@ibanking.local");
            helper.setTo(payer.getEmail());
            helper.setSubject("iBanking Tuition Payment – New OTP Verification");
            String amountStr = String.format("%,.0f VND", txn.getAmount().doubleValue());
            String html = """
                <div style='background:#0b1020;padding:24px;font-family:Inter,system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#e5e7eb;'>
                  <div style='max-width:640px;margin:0 auto;background:#11162a;border:1px solid rgba(255,255,255,.08);border-radius:16px;overflow:hidden;'>
                    <div style='padding:20px 24px;border-bottom:1px solid rgba(255,255,255,.08);display:flex;align-items:center;gap:10px;'>
                      <span style='display:inline-block;width:10px;height:10px;background:#3b82f6;border-radius:50%'></span>
                      <strong style='font-size:16px;color:#fff'>New OTP Generated</strong>
                    </div>
                    <div style='padding:24px;color:#d1d5db;'>
                      <p style='margin:0 0 12px 0;'>Hi <strong style='color:#fff'>%s</strong>,</p>
                      <p style='margin:0 0 16px 0;'>A new OTP has been generated for your tuition payment.</p>
                      <div style='margin:16px 0 20px 0;padding:14px 18px;background:#0f172a;border:1px solid rgba(255,255,255,.08);border-radius:12px;text-align:center;'>
                        <div style='font-size:13px;letter-spacing:.02em;color:#94a3b8;margin-bottom:6px;'>Your New OTP</div>
                        <div style='font-size:28px;letter-spacing:.3em;color:#fff;font-weight:700'>%s</div>
                      </div>
                      <table style='width:100%%;font-size:14px;color:#d1d5db;margin:12px 0 20px 0;'>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Transaction ID</td><td style='text-align:right;color:#fff'>%d</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Student ID</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Semester</td><td style='text-align:right;color:#fff'>%s</td></tr>
                        <tr><td style='padding:6px 0;color:#9ca3af'>Amount</td><td style='text-align:right;color:#fff'>%s</td></tr>
                      </table>
                      <p style='margin:0 0 8px 0;font-size:12px;color:#94a3b8;'>This new OTP will expire in %d seconds.</p>
                      <p style='margin:0 0 0 0;font-size:12px;color:#94a3b8;'>If you didn't request this, please contact support immediately.</p>
                    </div>
                  </div>
                  <div style='max-width:640px;margin:12px auto 0 auto;text-align:center;font-size:12px;color:#8b93a7;'>
                    © %d iBanking. All rights reserved.
                  </div>
                </div>
            """.formatted(
                    payer.getFullName(),
                    otp,
                    txn.getId(),
                    txn.getStudentId(),
                    txn.getSemester(),
                    amountStr,
                    otpTtlSeconds,
                    java.time.LocalDate.now().getYear()
            );
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            System.err.println("[MAIL] Failed to send new OTP email, reason: " + e.getMessage());
            try {
                var fallback = mailSender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(fallback, false, "UTF-8");
                h.setFrom("no-reply@ibanking.local");
                h.setTo(payer.getEmail());
                h.setSubject("New OTP for tuition payment");
                h.setText("Your new OTP is: " + otp + "\nTransaction ID: " + txn.getId());
                mailSender.send(fallback);
            } catch (Exception ignored) {}
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

    public record InitiateRequest(@NotBlank @Pattern(regexp = "^.{8}$") String studentId) {}
    public record ConfirmRequest(Long transactionId, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {}
    public record ResendOtpRequest(Long transactionId) {}
}


