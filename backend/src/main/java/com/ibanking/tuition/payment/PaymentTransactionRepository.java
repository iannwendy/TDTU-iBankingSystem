package com.ibanking.tuition.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    java.util.List<PaymentTransaction> findByPayerCustomerIdOrderByCreatedAtDesc(Long payerCustomerId);
}


