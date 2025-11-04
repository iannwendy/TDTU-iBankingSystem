package com.ibanking.tuition.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    java.util.List<PaymentTransaction> findByPayerCustomerIdOrderByCreatedAtDesc(Long payerCustomerId);
    java.util.List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentTransaction.Status status, OffsetDateTime createdAt);
    
    // Find pending transactions for the same student and semester
    List<PaymentTransaction> findByStudentIdAndSemesterAndStatusIn(
        String studentId, 
        String semester, 
        List<PaymentTransaction.Status> statuses
    );
}


