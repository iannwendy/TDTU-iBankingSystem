package com.ibanking.tuition.config;

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
            // Add alice and bob if they don't exist
            if (customers.findByUsername("alice").isEmpty()) {
                Customer a = new Customer();
                a.setUsername("alice");
                a.setPasswordHash(encoder.encode("password"));
                a.setFullName("Alice Nguyen");
                a.setPhone("0912345678");
                a.setEmail("alice@example.com");
                a.setBalance(new BigDecimal("20000000"));
                customers.save(a);
            }

            if (customers.findByUsername("bob").isEmpty()) {
                Customer b = new Customer();
                b.setUsername("bob");
                b.setPasswordHash(encoder.encode("password"));
                b.setFullName("Bob Tran");
                b.setPhone("0987654321");
                b.setEmail("bob@example.com");
                b.setBalance(new BigDecimal("10000000"));
                customers.save(b);
            }

            // Seed student accounts MSSV 523H0111 .. 523H0123 with external email
            System.out.println("Starting to seed students 523H0111-523H0123...");
            for (int i = 111; i <= 123; i++) {
                String suffix = String.format("%04d", i);
                String mssv = "523H" + suffix;
                
                if (customers.findByUsername(mssv).isPresent()) {
                    System.out.println("Student " + mssv + " already exists, skipping...");
                    continue;
                }

                System.out.println("Creating student " + mssv + "...");
                Customer c = new Customer();
                c.setUsername(mssv);
                c.setPasswordHash(encoder.encode("pass123"));
                c.setFullName(generateVietnameseName(i));
                c.setBalance(new BigDecimal("15000000"));
                c.setPhone(generatePhone(i));
                c.setEmail("iannwendii@gmail.com"); // All students use the same external email
                customers.save(c);
                System.out.println("Student " + mssv + " created successfully!");
            }

            // Add specific student 523H0054 with external email
            if (customers.findByUsername("523H0054").isEmpty()) {
                System.out.println("Adding student 523H0054...");
                Customer student0054 = new Customer();
                student0054.setUsername("523H0054");
                student0054.setPasswordHash(encoder.encode("pass123"));
                student0054.setFullName("Nguyễn Bảo Minh");
                student0054.setPhone("0900000054");
                student0054.setEmail("iannwendii@gmail.com"); // Updated to external email
                student0054.setBalance(new BigDecimal("1000000000"));
                customers.save(student0054);
                System.out.println("Student 523H0054 added successfully!");
            } else {
                System.out.println("Student 523H0054 already exists!");
            }

            // Add specific student 523H1234
            if (customers.findByUsername("523H1234").isEmpty()) {
                System.out.println("Adding student 523H1234...");
                Customer newStudent = new Customer();
                newStudent.setUsername("523H1234");
                newStudent.setPasswordHash(encoder.encode("pass123"));
                newStudent.setFullName("Nguyễn Bảo Minh");
                newStudent.setPhone("0900001234");
                newStudent.setEmail("iannwendii@gmail.com");
                newStudent.setBalance(new BigDecimal("1000000000")); // 1 billion VND
                customers.save(newStudent);
                System.out.println("Student 523H1234 added successfully!");
            } else {
                System.out.println("Student 523H1234 already exists!");
            }

            // Seed tuition records for HK1-2526 only
            String fixedSemester = "HK1-2526";
            
            // Add tuition records for students 523H0111 .. 523H0123
            for (int i = 111; i <= 123; i++) {
                String mssv = "523H" + String.format("%04d", i);
                if (tuitions.findByStudentIdAndSemester(mssv, fixedSemester).isPresent()) continue;
                StudentTuition st = new StudentTuition();
                st.setStudentId(mssv);
                st.setStudentName(generateVietnameseName(i));
                st.setSemester(fixedSemester);
                // Random amount between 7,000,000 and 15,000,000 step 100,000
                int steps = 70 + (i * 37 % 81); // deterministic pseudo-random
                BigDecimal amount = new BigDecimal(steps * 100_000);
                st.setAmount(amount);
                st.setPaid(false);
                tuitions.save(st);
            }

            // Add tuition record for student 523H0054
            if (tuitions.findByStudentIdAndSemester("523H0054", fixedSemester).isEmpty()) {
                System.out.println("Adding tuition record for student 523H0054...");
                StudentTuition student0054Tuition = new StudentTuition();
                student0054Tuition.setStudentId("523H0054");
                student0054Tuition.setStudentName("Nguyễn Bảo Minh");
                student0054Tuition.setSemester(fixedSemester);
                student0054Tuition.setAmount(new BigDecimal("12400000")); // 12.4 million VND
                student0054Tuition.setPaid(false);
                tuitions.save(student0054Tuition);
                System.out.println("Tuition record for student 523H0054 added successfully!");
            } else {
                System.out.println("Tuition record for student 523H0054 already exists!");
            }

            // Add tuition record for student 523H1234
            if (tuitions.findByStudentIdAndSemester("523H1234", fixedSemester).isEmpty()) {
                System.out.println("Adding tuition record for student 523H1234...");
                StudentTuition newStudentTuition = new StudentTuition();
                newStudentTuition.setStudentId("523H1234");
                newStudentTuition.setStudentName("Nguyễn Bảo Minh");
                newStudentTuition.setSemester(fixedSemester);
                newStudentTuition.setAmount(new BigDecimal("10000000")); // 10 million VND
                newStudentTuition.setPaid(false);
                tuitions.save(newStudentTuition);
                System.out.println("Tuition record for student 523H1234 added successfully!");
            } else {
                System.out.println("Tuition record for student 523H1234 already exists!");
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


