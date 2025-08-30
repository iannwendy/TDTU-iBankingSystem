package com.ibanking.tuition.tuition;

import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tuition")
public class TuitionController {

    private final StudentTuitionRepository studentTuitionRepository;

    public TuitionController(StudentTuitionRepository studentTuitionRepository) {
        this.studentTuitionRepository = studentTuitionRepository;
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam("studentId") @Pattern(regexp = "^.{8}$") String studentId) {
        String currentSemester = SemesterUtil.currentSemester();
        String normalized = studentId.trim().toUpperCase();
        return studentTuitionRepository.findByStudentIdAndSemester(normalized, currentSemester)
                .<ResponseEntity<?>>map(t -> {
                    if (t.isPaid()) {
                        return ResponseEntity.ok(Map.of(
                                "studentId", t.getStudentId(),
                                "studentName", t.getStudentName(),
                                "semester", t.getSemester(),
                                "amount", java.math.BigDecimal.ZERO,
                                "paid", true
                        ));
                    }
                    return ResponseEntity.ok(Map.of(
                            "studentId", t.getStudentId(),
                            "studentName", t.getStudentName(),
                            "semester", t.getSemester(),
                            "amount", t.getAmount(),
                            "paid", false
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "Student not found")));
    }
}


