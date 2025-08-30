package com.ibanking.tuition.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long payerCustomerId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String semester;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime completedAt;

    @Column(nullable = false)
    private String lockId; // Unique identifier for distributed locking

    @Column(nullable = false)
    private OffsetDateTime lockExpiry; // When the lock expires

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public enum Status { PENDING_OTP, SUCCESS, FAILED, EXPIRED, PROCESSING }

    // Default constructor
    public PaymentTransaction() {}

    // Getters
    public Long getId() { return id; }
    public Long getPayerCustomerId() { return payerCustomerId; }
    public String getStudentId() { return studentId; }
    public String getSemester() { return semester; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getLockId() { return lockId; }
    public OffsetDateTime getLockExpiry() { return lockExpiry; }
    public Long getVersion() { return version; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setPayerCustomerId(Long payerCustomerId) { this.payerCustomerId = payerCustomerId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setSemester(String semester) { this.semester = semester; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setStatus(Status status) { this.status = status; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public void setLockId(String lockId) { this.lockId = lockId; }
    public void setLockExpiry(OffsetDateTime lockExpiry) { this.lockExpiry = lockExpiry; }
    public void setVersion(Long version) { this.version = version; }
}


