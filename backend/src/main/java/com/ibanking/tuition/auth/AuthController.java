package com.ibanking.tuition.auth;

import com.ibanking.tuition.security.JwtService;
import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, CustomerRepository customerRepository, PasswordEncoder passwordEncoder, UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails.getUsername());
        Customer c = customerRepository.findByUsernameIgnoreCase(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "token", token,
                "fullName", c.getFullName(),
                "phone", c.getPhone(),
                "email", c.getEmail(),
                "balance", c.getBalance()
        ));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        Customer c = customerRepository.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "fullName", c.getFullName(),
                "phone", c.getPhone(),
                "email", c.getEmail(),
                "balance", c.getBalance()
        ));
    }

    @GetMapping("/seed-students")
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

    @GetMapping("/update-523h0054-email")
    public ResponseEntity<?> update523H0054Email() {
        try {
            Customer student = customerRepository.findByUsername("523H0054").orElse(null);
            if (student == null) {
                return ResponseEntity.status(404).body(Map.of("message", "Student 523H0054 not found"));
            }
            
            String oldEmail = student.getEmail();
            student.setEmail("iannwendii@gmail.com");
            customerRepository.save(student);
            
            return ResponseEntity.ok(Map.of(
                "message", "Email updated successfully",
                "oldEmail", oldEmail,
                "newEmail", "iannwendii@gmail.com"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error updating email: " + e.getMessage()));
        }
    }

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
}


