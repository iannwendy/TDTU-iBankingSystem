package com.ibanking.tuition.tuition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentTuitionRepository extends JpaRepository<StudentTuition, Long> {
    Optional<StudentTuition> findByStudentIdAndSemesterAndPaidIsFalse(String studentId, String semester);
    Optional<StudentTuition> findByStudentIdAndSemester(String studentId, String semester);
}


