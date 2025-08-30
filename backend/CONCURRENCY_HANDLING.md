# Concurrency Handling in iBanking Tuition Payment System

## Overview
This document explains how the iBanking Tuition Payment System handles concurrent transactions and prevents conflicts to ensure data consistency and integrity.

## Key Challenges Addressed

### 1. Race Conditions on Account Balance
- **Problem**: Multiple transactions trying to deduct from the same account simultaneously
- **Solution**: Distributed locking with Redis + Optimistic locking with JPA version field

### 2. Double Payment Prevention
- **Problem**: Same tuition being paid multiple times
- **Solution**: Status-based transaction state management + Database constraints

### 3. Concurrent MSSV Processing
- **Problem**: Multiple accounts trying to pay for the same student ID simultaneously
- **Solution**: Resource-level locking (tuition-specific locks)

## Architecture Components

### 1. Distributed Locking (Redis)
```java
// Lock keys for different resources
String payerLockKey = "lock:payer:{customerId}"
String tuitionLockKey = "lock:tuition:{studentId}:{semester}"
```

**Features:**
- Automatic expiration (30 seconds)
- Retry mechanism with exponential backoff
- Atomic lock acquisition

### 2. Optimistic Locking (JPA)
```java
@Version
@Column(nullable = false)
private Long version = 0L;
```

**How it works:**
- Each entity has a version field
- JPA automatically increments version on each update
- If version mismatch detected, `ObjectOptimisticLockingFailureException` is thrown
- Prevents "lost update" scenarios

### 3. Transaction Isolation Levels
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public boolean processPayment(Long transactionId)
```

**Isolation Levels Used:**
- **SERIALIZABLE**: For payment processing (highest isolation)
- **READ_COMMITTED**: For read operations and OTP management

## Flow Diagram

```
User Initiates Payment
        ↓
   Acquire Locks
   (Payer + Tuition)
        ↓
   Validate Resources
        ↓
   Create Transaction
        ↓
   Send OTP Email
        ↓
   Release Locks
        ↓
User Confirms with OTP
        ↓
   Acquire Locks Again
        ↓
   Process Payment
   (Atomic Operation)
        ↓
   Update Status
        ↓
   Release Locks
```

## Lock Acquisition Strategy

### 1. Hierarchical Locking
```java
// Always acquire locks in consistent order to prevent deadlocks
String payerLockKey = lockKey("payer", String.valueOf(payer.getId()));
String tuitionLockKey = lockKey("tuition", normalized + ":" + currentSemester);

boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);
```

### 2. Retry Mechanism
```java
public boolean tryAcquireLockWithRetry(String lockKey, int maxRetries) {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        if (acquireLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
            return true;
        }
        
        if (attempt < maxRetries - 1) {
            TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_MILLIS * (attempt + 1));
        }
    }
    return false;
}
```

## Error Handling

### 1. Concurrent Modification Detection
```java
try {
    boolean success = paymentService.processPayment(txn.getId());
    // ... success handling
} catch (RuntimeException e) {
    if (e.getMessage().contains("Concurrent modification")) {
        return ResponseEntity.status(409).body(
            Map.of("message", "Transaction conflict detected, please retry")
        );
    }
    // ... other error handling
}
```

### 2. HTTP Status Codes
- **409 Conflict**: Concurrent modification detected
- **423 Locked**: Resource busy, retry later
- **500 Internal Server Error**: Processing failure

## Performance Considerations

### 1. Lock Timeout
- **Default**: 30 seconds
- **Rationale**: Balance between preventing conflicts and allowing reasonable response time

### 2. Retry Delays
- **Initial**: 100ms
- **Exponential**: 200ms, 400ms
- **Max Retries**: 3 attempts

### 3. Transaction Timeout
- **Payment Processing**: 30 seconds
- **Read Operations**: No timeout (default)

## Monitoring and Debugging

### 1. Lock Status Tracking
```java
@Column(nullable = false)
private String lockId; // Unique identifier for distributed locking

@Column(nullable = false)
private OffsetDateTime lockExpiry; // When the lock expires
```

### 2. Transaction States
```java
public enum Status { 
    PENDING_OTP,    // Waiting for OTP verification
    PROCESSING,     // Payment being processed (locked)
    SUCCESS,        // Payment completed successfully
    FAILED,         // Payment failed
    EXPIRED         // OTP expired
}
```

## Best Practices

### 1. Always Release Locks
```java
try {
    // Critical operations
} finally {
    // Always release locks
    paymentService.releaseLock(payerLockKey);
    paymentService.releaseLock(tuitionLockKey);
}
```

### 2. Validate Before Processing
```java
// Double-check resources before processing
if (!paymentService.hasSufficientBalance(payer.getId(), t.getAmount())) {
    throw new IllegalStateException("Insufficient balance");
}
```

### 3. Use Appropriate Isolation Levels
- **SERIALIZABLE**: For financial transactions
- **READ_COMMITTED**: For read operations
- **READ_UNCOMMITTED**: Never use for financial data

## Testing Scenarios

### 1. Concurrent Payment Attempts
- Two users try to pay for the same tuition simultaneously
- Expected: One succeeds, one gets 423 status

### 2. Insufficient Balance Race
- User initiates payment, balance changes before confirmation
- Expected: Payment fails with appropriate error message

### 3. OTP Expiration
- User confirms payment after OTP expires
- Expected: Payment rejected with expiration message

## Future Enhancements

### 1. Deadlock Detection
- Implement deadlock detection algorithms
- Automatic lock release on deadlock detection

### 2. Lock Monitoring
- Redis-based lock monitoring dashboard
- Metrics for lock acquisition success/failure rates

### 3. Adaptive Timeouts
- Dynamic lock timeout based on system load
- Machine learning-based retry strategy optimization
