package com.ibanking.tuition.tuition;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "student_tuition")
public class StudentTuition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 8, nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String semester; // e.g., 2024-1

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean paid;

    @Column
    private LocalDate paidDate;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    // Default constructor
    public StudentTuition() {}

    // Getters
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getSemester() { return semester; }
    public BigDecimal getAmount() { return amount; }
    public boolean isPaid() { return paid; }
    public LocalDate getPaidDate() { return paidDate; }
    public Long getVersion() { return version; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setSemester(String semester) { this.semester = semester; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    public void setVersion(Long version) { this.version = version; }
}


