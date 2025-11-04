# Tóm Tắt Các Cơ Chế Tránh Đụng Độ (Concurrency Control Mechanisms)

## Tổng Quan
Hệ thống iBanking Tuition Payment sử dụng **nhiều lớp bảo vệ** để ngăn chặn các vấn đề đụng độ khi xử lý thanh toán đồng thời.

---

## 1. REDIS DISTRIBUTED LOCKING (Lớp 1)

### Mục đích
- Ngăn chặn nhiều request xử lý cùng một resource (payer/tuition) đồng thời
- Đảm bảo chỉ một request được xử lý tại một thời điểm cho mỗi resource

### Implementation
```java
// Lock keys
String payerLockKey = "lock:payer:{customerId}"
String tuitionLockKey = "lock:tuition:{studentId}:{semester}"

// Acquire lock với retry mechanism
boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);
```

### Đặc điểm
- **Timeout**: 30 giây (LOCK_TIMEOUT_SECONDS)
- **Retry**: Tối đa 3 lần với exponential backoff (100ms, 200ms, 400ms)
- **Atomic**: Sử dụng Redis `SETNX` (SET if Not eXists)
- **Auto-release**: Tự động expire sau 30s nếu không được release

### Nơi sử dụng
- **initiate()**: Acquire lock cho payer + tuition trước khi tạo transaction
- **confirm()**: Re-acquire lock trước khi process payment

---

## 2. DATABASE TRANSACTION ISOLATION (Lớp 2)

### Mục đích
- Đảm bảo tính atomic của các operations trong database
- Ngăn chặn dirty reads, non-repeatable reads, và phantom reads

### Implementation
```java
@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
public ResponseEntity<?> initiate(...) { ... }

@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
public boolean processPayment(Long transactionId) { ... }
```

### Isolation Level: SERIALIZABLE
- **Cao nhất**: Đảm bảo các transaction được thực thi tuần tự
- **Ứng dụng**: Payment processing (tài chính cần độ chính xác cao)
- **Trade-off**: Hiệu năng thấp hơn nhưng đảm bảo data consistency

---

## 3. OPTIMISTIC LOCKING (Lớp 3)

### Mục đích
- Phát hiện khi entity bị thay đổi bởi transaction khác
- Ngăn chặn "lost update" scenarios

### Implementation
```java
@Entity
public class PaymentTransaction {
    @Version
    @Column(nullable = false)
    private Long version = 0L;  // JPA tự động increment mỗi lần update
}
```

### Cơ chế hoạt động
1. JPA tự động tăng `version` mỗi khi entity được update
2. Khi update, JPA kiểm tra `version` có khớp không
3. Nếu không khớp → `ObjectOptimisticLockingFailureException`
4. Transaction bị rollback và thông báo lỗi

### Xử lý lỗi
```java
catch (ObjectOptimisticLockingFailureException e) {
    transaction.setStatus(PaymentTransaction.Status.FAILED);
    throw new RuntimeException("Concurrent modification detected, please retry");
}
```

---

## 4. PAYER-LEVEL TRANSACTION CHECK (Lớp 4)

### Mục đích
- **Một tài khoản chỉ được có 1 transaction pending tại một thời điểm**
- Ngăn chặn user tạo nhiều transaction đồng thời từ các tab/session khác nhau

### Implementation
```java
// Check payer-level pending transactions
List<PaymentTransaction> payerPendingTransactions = paymentTransactionRepository
    .findByPayerCustomerIdAndStatusIn(payer.getId(), pendingStatuses);

if (!payerPendingTransactions.isEmpty()) {
    return ResponseEntity.status(409).body(Map.of(
        "message", 
        "You already have a pending payment transaction (ID: " + existingTxn.getId() + 
        "). Please complete or cancel it before creating a new transaction."
    ));
}
```

### Repository Method
```java
List<PaymentTransaction> findByPayerCustomerIdAndStatusIn(
    Long payerCustomerId,
    List<PaymentTransaction.Status> statuses
);
```

### Status được check
- `PENDING_OTP`: Đang chờ OTP
- `PROCESSING`: Đang xử lý payment

---

## 5. STUDENT-LEVEL TRANSACTION CHECK (Lớp 5)

### Mục đích
- **Một student chỉ được có 1 transaction pending tại một thời điểm**
- Ngăn chặn nhiều người cùng thanh toán cho cùng một student

### Implementation
```java
// Check student-level pending transactions (NGAY TRƯỚC KHI SAVE)
List<PaymentTransaction> existingPending = paymentTransactionRepository
    .findByStudentIdAndSemesterAndStatusIn(normalized, currentSemester, pendingStatuses);

if (!existingPending.isEmpty()) {
    return ResponseEntity.status(409).body(Map.of(
        "message", 
        "There is already a pending payment transaction for this student..."
    ));
}

// Flush ngay sau save để transaction visible
paymentTransactionRepository.save(txn);
paymentTransactionRepository.flush();  // CRITICAL: Đảm bảo transaction visible ngay
```

### Repository Method
```java
List<PaymentTransaction> findByStudentIdAndSemesterAndStatusIn(
    String studentId, 
    String semester, 
    List<PaymentTransaction.Status> statuses
);
```

### Vị trí check
- **CRITICAL**: Check ngay trước khi `save()` để giảm race condition window
- **Flush**: Sau khi save, flush ngay để transaction visible trong cùng transaction

---

## 6. STATUS-BASED LOCKING (Lớp 6)

### Mục đích
- Ngăn chặn double processing của cùng một transaction
- Đảm bảo transaction chỉ được process một lần

### Implementation
```java
// Trong processPayment()
if (transaction.getStatus() != PaymentTransaction.Status.PENDING_OTP) {
    throw new IllegalStateException("Transaction is not in correct status");
}

// Update status ngay lập tức để lock transaction
transaction.setStatus(PaymentTransaction.Status.PROCESSING);
transaction.setLockId(UUID.randomUUID().toString());
transaction.setLockExpiry(OffsetDateTime.now().plusSeconds(30));
paymentTransactionRepository.save(transaction);
```

### Transaction States
- `PENDING_OTP`: Chờ OTP verification
- `PROCESSING`: Đang xử lý (locked, không thể process lại)
- `SUCCESS`: Thành công
- `FAILED`: Thất bại
- `EXPIRED`: OTP hết hạn

---

## 7. DOUBLE-CHECK PATTERN (Lớp 7)

### Mục đích
- Kiểm tra lại resource trước khi thực hiện operation quan trọng
- Đảm bảo điều kiện vẫn đúng sau khi acquire lock

### Implementation
```java
// Trong processPayment()
// Check 1: Trước khi acquire lock (trong initiate)
if (!paymentService.hasSufficientBalance(payer.getId(), t.getAmount())) {
    return ResponseEntity.status(400).body(...);
}

// Check 2: Sau khi acquire lock (trong processPayment)
if (customer.getBalance().compareTo(transaction.getAmount()) < 0) {
    transaction.setStatus(PaymentTransaction.Status.FAILED);
    throw new IllegalStateException("Insufficient balance");
}
```

### Nơi áp dụng
- Balance check: Check trước initiate và sau khi acquire lock
- Tuition availability: Check trước initiate và trong processPayment

---

## 8. LOCK RELEASE AFTER COMMIT (Lớp 8)

### Mục đích
- Đảm bảo lock chỉ được release sau khi transaction commit thành công
- Tránh race condition giữa commit và lock release

### Implementation
```java
// Register synchronization callback BEFORE creating transaction
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        paymentService.releaseLock(payerLockKey);
        paymentService.releaseLock(tuitionLockKey);
    }
    
    @Override
    public void afterCompletion(int status) {
        // Release locks on rollback too
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
            paymentService.releaseLock(payerLockKey);
            paymentService.releaseLock(tuitionLockKey);
        }
    }
});
```

### Lợi ích
- Lock được giữ cho đến khi transaction commit
- Nếu rollback, lock vẫn được release
- Đảm bảo tính nhất quán

---

## 9. LOGIN-TIME TRANSACTION RESTORATION (Lớp 9)

### Mục đích
- Ngăn chặn user login từ nhiều nơi và tạo transaction mới
- Đảm bảo tất cả sessions của cùng user đều thấy cùng transaction pending

### Implementation
```java
// Trong AuthController.login()
List<PaymentTransaction> pendingTransactions = paymentTransactionRepository
    .findByPayerCustomerIdAndStatusIn(c.getId(), pendingStatuses);

if (!pendingTransactions.isEmpty()) {
    PaymentTransaction pendingTxn = pendingTransactions.get(0);
    response.put("pendingTransactionId", pendingTxn.getId());
    response.put("pendingTransactionStatus", pendingTxn.getStatus().name());
}
```

### Frontend handling
- Khi login, nếu có `pendingTransactionId` → restore transaction
- Disable tạo transaction mới
- Hiển thị OTP popup với transaction cũ

---

## 10. FRONTEND STATE MANAGEMENT (Lớp 10)

### Mục đích
- Ngăn chặn user tạo transaction mới từ UI khi đang có transaction pending
- Auto-unlock khi transaction expire/fail

### Implementation
```typescript
// Disable inputs khi có transaction pending
disabled={!!transactionId}

// Poll transaction status mỗi 5 giây
useEffect(() => {
    const checkTransactionStatus = async () => {
        const txn = res.data?.find((t: any) => t.id === transactionId);
        if (!txn || (txn.status !== 'PENDING_OTP' && txn.status !== 'PROCESSING')) {
            // Auto-unlock
            setTransactionId(null);
        }
    };
    const interval = setInterval(checkTransactionStatus, 5000);
    return () => clearInterval(interval);
}, [transactionId, token]);
```

---

## Flow Diagram - Multi-Layer Protection

```
┌─────────────────────────────────────────────────────────────┐
│                    User Initiates Payment                    │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  1. REDIS LOCK (Payer + Tuition)       │ ← Lớp 1
        │     - Try acquire với retry (3 lần)   │
        │     - Timeout: 30s                     │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  2. DB TRANSACTION (SERIALIZABLE)     │ ← Lớp 2
        │     - Atomic operations               │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  3. PAYER-LEVEL CHECK                 │ ← Lớp 4
        │     - Check pending transactions      │
        │     - Reject nếu có pending           │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  4. STUDENT-LEVEL CHECK               │ ← Lớp 5
        │     - Check ngay trước save           │
        │     - Flush để visible ngay           │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  5. CREATE TRANSACTION                 │
        │     - Status: PENDING_OTP             │
        │     - Version field (Optimistic Lock)│ ← Lớp 3
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  6. RELEASE LOCKS (after commit)       │ ← Lớp 8
        │     - TransactionSynchronization       │
        └───────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    User Confirms with OTP                    │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  1. RE-ACQUIRE LOCKS                  │ ← Lớp 1
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  2. STATUS CHECK                       │ ← Lớp 6
        │     - Must be PENDING_OTP             │
        │     - Update to PROCESSING            │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  3. DOUBLE-CHECK                       │ ← Lớp 7
        │     - Balance check lại               │
        │     - Tuition availability check       │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  4. PROCESS PAYMENT (SERIALIZABLE)     │ ← Lớp 2
        │     - Optimistic lock check            │ ← Lớp 3
        │     - Atomic update                    │
        └───────────────────┬─────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  5. RELEASE LOCKS                      │ ← Lớp 8
        └───────────────────────────────────────┘
```

---

## Tóm Tắt Các Lớp Bảo Vệ

| Lớp | Cơ Chế | Mục Đích | Vị Trí |
|-----|--------|----------|--------|
| **1** | Redis Distributed Lock | Ngăn concurrent access | initiate(), confirm() |
| **2** | DB Isolation (SERIALIZABLE) | Atomic operations | initiate(), processPayment() |
| **3** | Optimistic Locking (@Version) | Detect concurrent modifications | Entity level |
| **4** | Payer-level Check | 1 transaction/payer | initiate() |
| **5** | Student-level Check | 1 transaction/student | initiate() (trước save) |
| **6** | Status-based Lock | Prevent double processing | processPayment() |
| **7** | Double-check Pattern | Verify conditions | initiate(), processPayment() |
| **8** | Lock Release After Commit | Consistency | TransactionSynchronization |
| **9** | Login-time Restoration | Multi-session sync | AuthController.login() |
| **10** | Frontend State Management | UI-level protection | Frontend polling |

---

## Kết Quả

Với **10 lớp bảo vệ** này, hệ thống đảm bảo:
- ✅ Không có double payment
- ✅ Không có race condition trên balance
- ✅ Không có concurrent transactions cho cùng payer/student
- ✅ Không có lost updates
- ✅ Data consistency cao nhất

---

## Test Scenarios Đã Được Bảo Vệ

1. **Hai tài khoản cùng thanh toán cho 1 student** → Reject transaction thứ 2
2. **Một tài khoản tạo 2 transactions** → Reject transaction thứ 2
3. **Login từ nhiều tab/session** → Tất cả đều thấy cùng transaction pending
4. **Concurrent balance deduction** → Optimistic lock + Redis lock ngăn chặn
5. **Transaction expired** → Auto-unlock ở tất cả sessions

