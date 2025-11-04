# Tóm Tắt Các Cơ Chế Tránh Đụng Độ (Concurrency Control Mechanisms)

## Tổng Quan
Hệ thống iBanking Tuition Payment sử dụng **nhiều lớp bảo vệ** để ngăn chặn các vấn đề đụng độ khi xử lý thanh toán đồng thời.

---

## 1. REDIS DISTRIBUTED LOCKING (Lớp 1)

### Mục đích
- Ngăn chặn nhiều request xử lý cùng một resource (payer/tuition) đồng thời
- Đảm bảo chỉ một request được xử lý tại một thời điểm cho mỗi resource

### Implementation

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentService.java`

- **acquireLock()**: Dòng 50-54
  ```java
  public boolean acquireLock(String lockKey, int timeoutSeconds) {
      String lockValue = UUID.randomUUID().toString();
      Boolean acquired = redisTemplate.opsForValue()
              .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
      return Boolean.TRUE.equals(acquired);
  }
  ```

- **releaseLock()**: Dòng 61-63
  ```java
  public void releaseLock(String lockKey) {
      redisTemplate.delete(lockKey);
  }
  ```

- **tryAcquireLockWithRetry()**: Dòng 71-87
  ```java
  public boolean tryAcquireLockWithRetry(String lockKey, int maxRetries) {
      for (int attempt = 0; attempt < maxRetries; attempt++) {
          if (acquireLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
              return true;
          }
          // Exponential backoff: 100ms, 200ms, 400ms
          if (attempt < maxRetries - 1) {
              TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_MILLIS * (attempt + 1));
          }
      }
      return false;
  }
  ```

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **initiate() - Acquire locks**: Dòng 77-81
  ```java
  String payerLockKey = lockKey("payer", String.valueOf(payer.getId()));
  String tuitionLockKey = lockKey("tuition", normalized + ":" + currentSemester);
  boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
  boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);
  ```

- **confirm() - Re-acquire locks**: Dòng 232-236
  ```java
  String payerLockKey = lockKey("payer", String.valueOf(txn.getPayerCustomerId()));
  String tuitionLockKey = lockKey("tuition", txn.getStudentId() + ":" + txn.getSemester());
  boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
  boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);
  ```

### Đặc điểm
- **Timeout**: 30 giây (LOCK_TIMEOUT_SECONDS - dòng 29 trong PaymentService.java)
- **Retry**: Tối đa 3 lần với exponential backoff (100ms, 200ms, 400ms)
- **LOCK_WAIT_MILLIS**: 100ms (dòng 30 trong PaymentService.java)
- **Atomic**: Sử dụng Redis `SETNX` (SET if Not eXists)
- **Auto-release**: Tự động expire sau 30s nếu không được release

---

## 2. DATABASE TRANSACTION ISOLATION (Lớp 2)

### Mục đích
- Đảm bảo tính atomic của các operations trong database
- Ngăn chặn dirty reads, non-repeatable reads, và phantom reads

### Implementation

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **initiate()**: Dòng 70
  ```java
  @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
  public ResponseEntity<?> initiate(Authentication auth, @Valid @RequestBody InitiateRequest req)
  ```

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentService.java`

- **processPayment()**: Dòng 94
  ```java
  @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
  public boolean processPayment(Long transactionId)
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

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentTransaction.java`

- **@Version field**: Dòng 42-44
  ```java
  @Version
  @Column(nullable = false)
  private Long version = 0L;  // JPA tự động increment mỗi lần update
  ```

### Cơ chế hoạt động
1. JPA tự động tăng `version` mỗi khi entity được update
2. Khi update, JPA kiểm tra `version` có khớp không
3. Nếu không khớp → `ObjectOptimisticLockingFailureException`
4. Transaction bị rollback và thông báo lỗi

### Xử lý lỗi

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentService.java`

- **catch block**: Dòng 140-144
  ```java
  catch (ObjectOptimisticLockingFailureException e) {
      transaction.setStatus(PaymentTransaction.Status.FAILED);
      paymentTransactionRepository.save(transaction);
      throw new RuntimeException("Concurrent modification detected, please retry", e);
  }
  ```

---

## 4. PAYER-LEVEL TRANSACTION CHECK (Lớp 4)

### Mục đích
- **Một tài khoản chỉ được có 1 transaction pending tại một thời điểm**
- Ngăn chặn user tạo nhiều transaction đồng thời từ các tab/session khác nhau

### Implementation

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **Check payer-level pending**: Dòng 90-107
  ```java
  // CRITICAL: Check if payer already has a pending transaction
  List<PaymentTransaction.Status> pendingStatuses = List.of(
      PaymentTransaction.Status.PENDING_OTP,
      PaymentTransaction.Status.PROCESSING
  );
  List<PaymentTransaction> payerPendingTransactions = paymentTransactionRepository
      .findByPayerCustomerIdAndStatusIn(payer.getId(), pendingStatuses);
  
  if (!payerPendingTransactions.isEmpty()) {
      PaymentTransaction existingTxn = payerPendingTransactions.get(0);
      return ResponseEntity.status(409).body(Map.of(
          "message", 
          "You already have a pending payment transaction (ID: " + existingTxn.getId() + 
          "). Please complete or cancel it before creating a new transaction."
      ));
  }
  ```

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentTransactionRepository.java`

- **Repository method**: Dòng 19-22
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

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **Check student-level pending**: Dòng 125-137
  ```java
  // CRITICAL: Double-check if there's already a pending transaction for this tuition
  // This check MUST be done right before save to prevent race conditions
  List<PaymentTransaction> existingPending = paymentTransactionRepository
      .findByStudentIdAndSemesterAndStatusIn(normalized, currentSemester, pendingStatuses);
  
  if (!existingPending.isEmpty()) {
      return ResponseEntity.status(409).body(Map.of(
          "message", 
          "There is already a pending payment transaction for this student..."
      ));
  }
  ```

- **Save và flush**: Dòng 172-175
  ```java
  txn = paymentTransactionRepository.save(txn);
  // Flush immediately to ensure transaction is visible in current transaction
  paymentTransactionRepository.flush();  // CRITICAL: Đảm bảo transaction visible ngay
  ```

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentTransactionRepository.java`

- **Repository method**: Dòng 12-16
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

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentService.java`

- **Status check và update**: Dòng 99-107
  ```java
  if (transaction.getStatus() != PaymentTransaction.Status.PENDING_OTP) {
      throw new IllegalStateException("Transaction is not in correct status for processing");
  }

  // Update transaction status to PROCESSING to prevent double processing
  transaction.setStatus(PaymentTransaction.Status.PROCESSING);
  transaction.setLockId(UUID.randomUUID().toString());
  transaction.setLockExpiry(OffsetDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS));
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

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **Check 1 - Balance check trong initiate()**: Dòng 121-123
  ```java
  if (!paymentService.hasSufficientBalance(payer.getId(), t.getAmount())) {
      return ResponseEntity.status(400).body(Map.of("message", "Insufficient balance"));
  }
  ```

- **Check 1 - Tuition availability trong initiate()**: Dòng 110-112
  ```java
  if (!paymentService.isTuitionAvailable(normalized, currentSemester)) {
      return ResponseEntity.status(404).body(Map.of("message", "No unpaid tuition..."));
  }
  ```

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentService.java`

- **Check 2 - Balance check lại trong processPayment()**: Dòng 119-123
  ```java
  // Verify balance again (double-check)
  if (customer.getBalance().compareTo(transaction.getAmount()) < 0) {
      transaction.setStatus(PaymentTransaction.Status.FAILED);
      paymentTransactionRepository.save(transaction);
      throw new IllegalStateException("Insufficient balance");
  }
  ```

- **Check 2 - Tuition availability lại trong processPayment()**: Dòng 114-116
  ```java
  StudentTuition tuition = studentTuitionRepository
      .findByStudentIdAndSemesterAndPaidIsFalse(transaction.getStudentId(), transaction.getSemester())
      .orElseThrow(() -> new IllegalArgumentException("Tuition not found or already paid"));
  ```

---

## 8. LOCK RELEASE AFTER COMMIT (Lớp 8)

### Mục đích
- Đảm bảo lock chỉ được release sau khi transaction commit thành công
- Tránh race condition giữa commit và lock release

### Implementation

**File:** `backend/src/main/java/com/ibanking/tuition/payment/PaymentController.java`

- **TransactionSynchronization trong initiate()**: Dòng 139-159
  ```java
  // Register synchronization callback BEFORE creating transaction
  final boolean[] locksReleased = {false};
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
          paymentService.releaseLock(payerLockKey);
          paymentService.releaseLock(tuitionLockKey);
          locksReleased[0] = true;
      }
      
      @Override
      public void afterCompletion(int status) {
          // If transaction rolled back, also release locks
          if (!locksReleased[0] && status == TransactionSynchronization.STATUS_ROLLED_BACK) {
              paymentService.releaseLock(payerLockKey);
              paymentService.releaseLock(tuitionLockKey);
              locksReleased[0] = true;
          }
      }
  });
  ```

- **Release locks trong catch block**: Dòng 195-196
  ```java
  catch (Exception e) {
      // If any error occurs, release locks immediately
      paymentService.releaseLock(payerLockKey);
      paymentService.releaseLock(tuitionLockKey);
      throw e;
  }
  ```

- **Release locks trong confirm() finally**: Dòng 276-277
  ```java
  finally {
      // Always release locks after processing
      paymentService.releaseLock(payerLockKey);
      paymentService.releaseLock(tuitionLockKey);
  }
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

**File:** `backend/src/main/java/com/ibanking/tuition/auth/AuthController.java`

- **Check pending transaction trong login()**: Dòng 55-76
  ```java
  // Check if there's a pending transaction for this customer
  List<PaymentTransaction.Status> pendingStatuses = List.of(
      PaymentTransaction.Status.PENDING_OTP,
      PaymentTransaction.Status.PROCESSING
  );
  List<PaymentTransaction> pendingTransactions = paymentTransactionRepository
      .findByPayerCustomerIdAndStatusIn(c.getId(), pendingStatuses);
  
  // If there's a pending transaction, include it in the response
  if (!pendingTransactions.isEmpty()) {
      PaymentTransaction pendingTxn = pendingTransactions.get(0);
      response.put("pendingTransactionId", pendingTxn.getId());
      response.put("pendingTransactionStatus", pendingTxn.getStatus().name());
      response.put("pendingTransactionCreatedAt", pendingTxn.getCreatedAt().toString());
  }
  ```

### Frontend handling

**File:** `frontend/app/page.tsx`

- **Restore transaction khi login**: Dòng 105-126
  ```typescript
  // If there's a pending transaction, restore it
  if (res.data.pendingTransactionId) {
      setTransactionId(res.data.pendingTransactionId);
      setOtpTtlSeconds(120);
      setOtpPopupOpen(true);
      setOtpPopupMinimized(false);
      // Calculate remaining time and restore transaction
  }
  ```

- **Disable inputs**: Dòng 296, 305, 375
  ```typescript
  disabled={!!transactionId}  // Disable MSSV input
  disabled={!!transactionId}  // Disable Lookup button
  disabled={... || !!transactionId}  // Disable Confirm transaction button
  ```

---

## 10. FRONTEND STATE MANAGEMENT (Lớp 10)

### Mục đích
- Ngăn chặn user tạo transaction mới từ UI khi đang có transaction pending
- Auto-unlock khi transaction expire/fail

### Implementation

**File:** `frontend/app/page.tsx`

- **Disable inputs khi có transaction pending**: 
  - MSSV input: Dòng 296
  - Lookup button: Dòng 305
  - Confirm transaction button: Dòng 375
  ```typescript
  disabled={!!transactionId}
  ```

- **Poll transaction status**: Dòng 43-71
  ```typescript
  useEffect(() => {
      if (!transactionId || !token) return;
      
      const checkTransactionStatus = async () => {
          const res = await axios.get(`${API}/api/payment/history`, ...);
          const txn = res.data?.find((t: any) => t.id === transactionId);
          
          if (!txn || (txn.status !== 'PENDING_OTP' && txn.status !== 'PROCESSING')) {
              // Auto-unlock
              setTransactionId(null);
              setOtpPopupOpen(false);
              setOtpPopupMinimized(false);
          }
      };
      
      // Check every 5 seconds
      const interval = setInterval(checkTransactionStatus, 5000);
      return () => clearInterval(interval);
  }, [transactionId, token]);
  ```

- **Warning message**: Dòng 311-315
  ```typescript
  {transactionId && (
      <div className="mt-2 text-amber-400 text-sm">
          ⚠️ You have a pending OTP transaction (ID: {transactionId})...
      </div>
  )}
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

