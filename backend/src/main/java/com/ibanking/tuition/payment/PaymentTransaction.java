package com.ibanking.tuition.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
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
}


