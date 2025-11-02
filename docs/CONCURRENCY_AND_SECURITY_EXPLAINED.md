# 🔒 GIẢI THÍCH CHI TIẾT: BẢO MẬT VÀ CONCURRENCY TRONG iBanking

## 📋 MỤC LỤC
1. [Distributed Locking với Redis](#1-distributed-locking-với-redis)
2. [Optimistic Locking với @Version](#2-optimistic-locking-với-version)
3. [Transaction Isolation SERIALIZABLE](#3-transaction-isolation-serializable)
4. [Cách Test](#4-cách-test)

---

## 1. DISTRIBUTED LOCKING VỚI REDIS

### 🔍 **Là gì?**
Distributed Locking là cơ chế đảm bảo chỉ có **một request duy nhất** được phép truy cập vào một tài nguyên tại một thời điểm, ngay cả khi có nhiều server/instance cùng chạy.

### 🎯 **Vấn đề giải quyết:**
- **Race Condition:** Nhiều người cùng thanh toán học phí cho cùng 1 sinh viên
- **Double Payment:** Tránh thanh toán trùng lặp
- **Balance Inconsistency:** Tránh trừ tiền nhiều lần

### 💻 **Hiện thực trong dự án:**

#### **A. Trong PaymentController.java (dòng 76-86):**
```java
// Tạo 2 locks: 1 cho payer, 1 cho tuition
String payerLockKey = lockKey("payer", String.valueOf(payer.getId()));
String tuitionLockKey = lockKey("tuition", normalized + ":" + currentSemester);

// Thử acquire lock với retry (tối đa 3 lần)
boolean payerLocked = paymentService.tryAcquireLockWithRetry(payerLockKey, 3);
boolean tuitionLocked = paymentService.tryAcquireLockWithRetry(tuitionLockKey, 3);

// Nếu không acquire được → trả về 423 (Resource Locked)
if (!(payerLocked && tuitionLocked)) {
    return ResponseEntity.status(423).body(Map.of("message", "Resource busy, please try again later"));
}
```

#### **B. Trong PaymentService.java (dòng 50-87):**

**1. Acquire Lock (dòng 50-55):**
```java
public boolean acquireLock(String lockKey, int timeoutSeconds) {
    String lockValue = UUID.randomUUID().toString();
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
    return Boolean.TRUE.equals(acquired);
}
```
- **setIfAbsent():** Chỉ set key nếu key chưa tồn tại (atomic operation)
- **Timeout 30 giây:** Lock tự động expire sau 30s để tránh deadlock

**2. Retry với Exponential Backoff (dòng 71-87):**
```java
public boolean tryAcquireLockWithRetry(String lockKey, int maxRetries) {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        if (acquireLock(lockKey, LOCK_TIMEOUT_SECONDS)) {
            return true;
        }
        
        if (attempt < maxRetries - 1) {
            TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_MILLIS * (attempt + 1));
            // Attempt 1: wait 100ms
            // Attempt 2: wait 200ms
            // Attempt 3: wait 300ms (exponential backoff)
        }
    }
    return false;
}
```

**3. Release Lock (dòng 61-63):**
```java
public void releaseLock(String lockKey) {
    redisTemplate.delete(lockKey);
}
```

### 📊 **Luồng hoạt động:**
```
User 1: Initiate Payment → Acquire Lock (payer:1, tuition:523H0054:HK1)
User 2: Initiate Payment → Try Acquire Lock → BUSY (retry 3 lần)
User 2: Retry 1 → Wait 100ms → Still BUSY
User 2: Retry 2 → Wait 200ms → Still BUSY  
User 2: Retry 3 → Wait 300ms → Still BUSY
User 2: Return 423 (Resource busy)
User 1: Complete → Release Lock
```

---

## 2. OPTIMISTIC LOCKING VỚI @VERSION

### 🔍 **Là gì?**
Optimistic Locking giả định rằng **ít khi có conflict**, nhưng khi có conflict thì sẽ phát hiện và xử lý.

### 🎯 **Vấn đề giải quyết:**
- **Lost Update:** Tránh mất dữ liệu khi nhiều request cùng update
- **Version Conflict:** Phát hiện khi có nhiều người cùng update cùng 1 record

### 💻 **Hiện thực trong dự án:**

#### **A. Customer.java (dòng 31-33):**
```java
@Version
@Column(nullable = false)
private Long version = 0L;
```
- **@Version:** Annotation của JPA, tự động tăng mỗi khi update
- **version = 0:** Khởi tạo với version 0

#### **B. PaymentTransaction.java (dòng 42-44):**
```java
@Version
@Column(nullable = false)
private Long version = 0L;
```

#### **C. PaymentService.java - Xử lý conflict (dòng 140-144):**
```java
try {
    // ... process payment ...
    customer.setBalance(customer.getBalance().subtract(transaction.getAmount()));
    customerRepository.save(customer); // ← Version tự động tăng
    
} catch (ObjectOptimisticLockingFailureException e) {
    // Phát hiện conflict: version không khớp
    transaction.setStatus(PaymentTransaction.Status.FAILED);
    paymentTransactionRepository.save(transaction);
    throw new RuntimeException("Concurrent modification detected, please retry", e);
}
```

### 📊 **Luồng hoạt động:**
```
Transaction 1: Read Customer (version=10, balance=10000000)
Transaction 2: Read Customer (version=10, balance=10000000)

Transaction 1: Update Customer → Save (version=11, balance=9500000) ✅
Transaction 2: Update Customer → Save → ❌ VERSION MISMATCH!
              → ObjectOptimisticLockingFailureException
              → Rollback Transaction 2
              → Return error: "Concurrent modification detected"
```

### 🔑 **Cơ chế:**
1. **READ:** JPA load entity với version hiện tại (ví dụ: version=10)
2. **UPDATE:** Khi save, JPA check: `WHERE id = ? AND version = ?`
3. **CONFLICT:** Nếu version không khớp → `ObjectOptimisticLockingFailureException`
4. **SUCCESS:** Nếu version khớp → Update và tăng version (version=11)

---

## 3. TRANSACTION ISOLATION SERIALIZABLE

### 🔍 **Là gì?**
`SERIALIZABLE` là isolation level **cao nhất** trong database, đảm bảo transactions chạy như thể chúng được **thực thi tuần tự**, không có interference.

### 🎯 **Vấn đề giải quyết:**
- **Dirty Read:** Đọc dữ liệu chưa commit
- **Non-repeatable Read:** Dữ liệu thay đổi giữa 2 lần đọc
- **Phantom Read:** Có record mới xuất hiện giữa 2 lần đọc
- **Lost Update:** Mất update khi 2 transaction cùng update

### 💻 **Hiện thực trong dự án:**

#### **PaymentService.java (dòng 94):**
```java
@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
public boolean processPayment(Long transactionId) {
    // Tất cả operations trong method này chạy trong 1 transaction
    // với isolation level SERIALIZABLE
    
    // 1. Load transaction
    PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId);
    
    // 2. Load customer (với lock)
    Customer customer = customerRepository.findById(transaction.getPayerCustomerId());
    
    // 3. Load tuition (với lock)
    StudentTuition tuition = studentTuitionRepository
        .findByStudentIdAndSemesterAndPaidIsFalse(...);
    
    // 4. Verify balance
    if (customer.getBalance().compareTo(transaction.getAmount()) < 0) {
        throw new IllegalStateException("Insufficient balance");
    }
    
    // 5. Update customer balance
    customer.setBalance(customer.getBalance().subtract(transaction.getAmount()));
    customerRepository.save(customer);
    
    // 6. Mark tuition as paid
    tuition.setPaid(true);
    studentTuitionRepository.save(tuition);
    
    // 7. Mark transaction as success
    transaction.setStatus(PaymentTransaction.Status.SUCCESS);
    paymentTransactionRepository.save(transaction);
    
    // Tất cả operations phải thành công → COMMIT
    // Nếu có bất kỳ lỗi nào → ROLLBACK (do rollbackFor = Exception.class)
}
```

### 📊 **Luồng hoạt động với SERIALIZABLE:**
```
Transaction 1 (T1): BEGIN
Transaction 2 (T2): BEGIN

T1: SELECT customer WHERE id=1 → (balance=10000000) [LOCK row]
T2: SELECT customer WHERE id=1 → ⏳ WAIT (row locked by T1)

T1: UPDATE customer SET balance=9500000 WHERE id=1 → COMMIT ✅
T2: ⏳ UNLOCK → SELECT customer WHERE id=1 → (balance=9500000) [LOCK row]
T2: UPDATE customer SET balance=9000000 WHERE id=1 → COMMIT ✅

→ Không có conflict, mỗi transaction chạy độc lập
```

### 🎯 **So sánh với các Isolation Level khác:**

| Isolation Level | Dirty Read | Non-repeatable | Phantom Read | Lost Update |
|----------------|------------|-----------------|--------------|-------------|
| READ UNCOMMITTED | ✅ Có thể | ✅ Có thể | ✅ Có thể | ✅ Có thể |
| READ COMMITTED | ❌ Không | ✅ Có thể | ✅ Có thể | ✅ Có thể |
| REPEATABLE READ | ❌ Không | ❌ Không | ✅ Có thể | ✅ Có thể |
| **SERIALIZABLE** | ❌ **Không** | ❌ **Không** | ❌ **Không** | ❌ **Không** |

### ⚠️ **Trade-off:**
- ✅ **Ưu điểm:** Đảm bảo tính nhất quán dữ liệu cao nhất
- ❌ **Nhược điểm:** Hiệu suất chậm hơn (do lock nhiều)

---

## 4. CÁCH TEST

Xem file `test-concurrency.sh` để test từng tính năng.

