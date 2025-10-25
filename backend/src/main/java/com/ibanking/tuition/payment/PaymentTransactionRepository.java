package com.ibanking.tuition.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    java.util.List<PaymentTransaction> findByPayerCustomerIdOrderByCreatedAtDesc(Long payerCustomerId);
    java.util.List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentTransaction.Status status, OffsetDateTime createdAt);
}


