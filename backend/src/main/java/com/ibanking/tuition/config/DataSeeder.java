package com.ibanking.tuition.config;

import com.ibanking.tuition.tuition.SemesterUtil;
import com.ibanking.tuition.tuition.StudentTuition;
import com.ibanking.tuition.tuition.StudentTuitionRepository;
import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(CustomerRepository customers, StudentTuitionRepository tuitions, PasswordEncoder encoder) {
        return args -> {
            if (customers.count() == 0) {
                Customer a = new Customer();
                a.setUsername("alice");
                a.setPasswordHash(encoder.encode("password"));
                a.setFullName("Alice Nguyen");
                a.setPhone("0912345678");
                a.setEmail("alice@example.com");
                a.setBalance(new BigDecimal("20000000"));
                customers.save(a);

                Customer b = new Customer();
                b.setUsername("bob");
                b.setPasswordHash(encoder.encode("password"));
                b.setFullName("Bob Tran");
                b.setPhone("0987654321");
                b.setEmail("bob@example.com");
                b.setBalance(new BigDecimal("10000000"));
                customers.save(b);
            }

            // Seed 100 student accounts MSSV 523H0001 .. 523H0100
            for (int i = 1; i <= 100; i++) {
                String suffix = String.format("%04d", i);
                String mssv = "523H" + suffix;
                if (customers.findByUsername(mssv).isPresent()) continue;

                Customer c = new Customer();
                c.setUsername(mssv);
                c.setPasswordHash(encoder.encode("pass123"));

                if ("523H0054".equals(mssv)) {
                    c.setFullName("Nguyễn Bảo Minh");
                    c.setBalance(new BigDecimal("1000000000"));
                } else {
                    c.setFullName(generateVietnameseName(i));
                    c.setBalance(new BigDecimal("15000000"));
                }

                c.setPhone(generatePhone(i));
                c.setEmail(mssv.toLowerCase() + "@student.tdtu.edu.vn");
                customers.save(c);
            }

            // Seed tuition records for HK1-2526 only
            String fixedSemester = "HK1-2526";
            for (int i = 1; i <= 100; i++) {
                String mssv = "523H" + String.format("%04d", i);
                if (tuitions.findByStudentIdAndSemester(mssv, fixedSemester).isPresent()) continue;
                StudentTuition st = new StudentTuition();
                st.setStudentId(mssv);
                st.setStudentName("523H0054".equals(mssv) ? "Nguyễn Bảo Minh" : generateVietnameseName(i));
                st.setSemester(fixedSemester);
                // Random amount between 7,000,000 and 15,000,000 step 100,000
                int steps = 70 + (i * 37 % 81); // deterministic pseudo-random
                BigDecimal amount = new BigDecimal(steps * 100_000);
                st.setAmount(amount);
                st.setPaid(false);
                tuitions.save(st);
            }
        };
    }

    private static String generatePhone(int idx) {
        // Simple deterministic phones like 0900000001.. ensuring 10 digits
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


