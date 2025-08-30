package com.ibanking.tuition.payment;

import com.ibanking.tuition.user.Customer;
import com.ibanking.tuition.user.CustomerRepository;
import com.ibanking.tuition.tuition.StudentTuition;
import com.ibanking.tuition.tuition.StudentTuitionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentService {

    private final CustomerRepository customerRepository;
    private final StudentTuitionRepository studentTuitionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StringRedisTemplate redisTemplate;

    private static final int LOCK_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long LOCK_WAIT_MILLIS = 100;

    public PaymentService(CustomerRepository customerRepository,
                         StudentTuitionRepository studentTuitionRepository,
                         PaymentTransactionRepository paymentTransactionRepository,
                         StringRedisTemplate redisTemplate) {
        this.customerRepository = customerRepository;
        this.studentTuitionRepository = studentTuitionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Acquire distributed lock for a resource
     * @param lockKey The key to lock
     * @param timeoutSeconds Lock timeout in seconds
     * @return true if lock acquired, false otherwise
     */
    public boolean acquireLock(String lockKey, int timeoutSeconds) {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Release distributed lock
     * @param lockKey The key to unlock
     */
    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    /**
     * Try to acquire lock with retry mechanism
     * @param lockKey The key to lock
     * @param maxRetries Maximum number of retry attempts
     * @return true if lock acquired, false otherwise
     */
    public boolean tryAcquireLockWithRetry(String lockKey, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (acquireLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
                return true;
            }
            
            if (attempt < maxRetries - 1) {
                try {
                    TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_MILLIS * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Process payment with proper concurrency control
     * @param transactionId The transaction ID to process
     * @return true if payment successful, false otherwise
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public boolean processPayment(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (transaction.getStatus() != PaymentTransaction.Status.PENDING_OTP) {
            throw new IllegalStateException("Transaction is not in correct status for processing");
        }

        // Update transaction status to PROCESSING to prevent double processing
        transaction.setStatus(PaymentTransaction.Status.PROCESSING);
        transaction.setLockId(UUID.randomUUID().toString());
        transaction.setLockExpiry(OffsetDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS));
        paymentTransactionRepository.save(transaction);

        try {
            // Get customer and tuition with optimistic locking
            Customer customer = customerRepository.findById(transaction.getPayerCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

            StudentTuition tuition = studentTuitionRepository
                    .findByStudentIdAndSemesterAndPaidIsFalse(transaction.getStudentId(), transaction.getSemester())
                    .orElseThrow(() -> new IllegalArgumentException("Tuition not found or already paid"));

            // Verify balance again (double-check)
            if (customer.getBalance().compareTo(transaction.getAmount()) < 0) {
                transaction.setStatus(PaymentTransaction.Status.FAILED);
                paymentTransactionRepository.save(transaction);
                throw new IllegalStateException("Insufficient balance");
            }

            // Process payment atomically
            customer.setBalance(customer.getBalance().subtract(transaction.getAmount()));
            customerRepository.save(customer);

            tuition.setPaid(true);
            tuition.setPaidDate(java.time.LocalDate.now());
            studentTuitionRepository.save(tuition);

            // Mark transaction as successful
            transaction.setStatus(PaymentTransaction.Status.SUCCESS);
            transaction.setCompletedAt(OffsetDateTime.now());
            paymentTransactionRepository.save(transaction);

            return true;

        } catch (ObjectOptimisticLockingFailureException e) {
            // Handle optimistic locking failure
            transaction.setStatus(PaymentTransaction.Status.FAILED);
            paymentTransactionRepository.save(transaction);
            throw new RuntimeException("Concurrent modification detected, please retry", e);
        } catch (DataIntegrityViolationException e) {
            // Handle data integrity violation
            transaction.setStatus(PaymentTransaction.Status.FAILED);
            paymentTransactionRepository.save(transaction);
            throw new RuntimeException("Data integrity violation", e);
        } catch (Exception e) {
            // Handle other exceptions
            transaction.setStatus(PaymentTransaction.Status.FAILED);
            paymentTransactionRepository.save(transaction);
            throw e;
        }
    }

    /**
     * Check if a customer has sufficient balance for payment
     * @param customerId Customer ID
     * @param amount Payment amount
     * @return true if sufficient balance, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(Long customerId, java.math.BigDecimal amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        return customer.getBalance().compareTo(amount) >= 0;
    }

    /**
     * Check if tuition is still available for payment
     * @param studentId Student ID
     * @param semester Semester
     * @return true if available, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isTuitionAvailable(String studentId, String semester) {
        return studentTuitionRepository
                .findByStudentIdAndSemesterAndPaidIsFalse(studentId, semester)
                .isPresent();
    }

    /**
     * Clean up expired locks
     */
    public void cleanupExpiredLocks() {
        // This method can be called periodically to clean up expired locks
        // Implementation depends on your specific requirements
    }
}
