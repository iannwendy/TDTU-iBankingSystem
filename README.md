# iBanking Tuition Payment System

A secure online tuition payment system built with Spring Boot (Java 21) and Next.js (TypeScript), featuring OTP email verification, distributed locking, and optimistic concurrency control.

## Features

- **Authentication**: JWT-based authentication with Spring Security
- **OTP Verification**: 6-digit OTP via email with 120-second expiration
- **Payment Processing**: Secure payment transactions with balance validation
- **Concurrency Control**: 
  - Distributed locking with Redis (30s timeout, exponential backoff retry)
  - Optimistic locking with JPA `@Version` annotation
  - Serializable transaction isolation for data consistency
- **Email Notifications**: HTML email templates for OTP and payment confirmation
- **Modern UI**: Responsive dark theme with OTP popup and real-time feedback

## Architecture

```
backend/          # Spring Boot REST API (Java 21)
frontend/         # Next.js application (TypeScript)
docker-compose.yml # Multi-container setup
```

### Tech Stack

- **Backend**: Spring Boot 3.3.2, Java 21, PostgreSQL, Redis
- **Frontend**: Next.js 14, React 18, Tailwind CSS
- **Security**: JWT, Spring Security, CORS
- **Email**: JavaMailSender (Gmail SMTP + MailPit for testing)

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21 (if running locally)
- Node.js 18+ (if running locally)

### Run with Docker (Recommended)

```bash
# Clone repository
git clone <repository-url>
cd SOA_iBankingSystem

# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

### Access Services

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **MailPit UI**: http://localhost:8025 (for testing emails)
- **PostgreSQL**: localhost:55432
- **Redis**: localhost:6379

### Local Development

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## API Endpoints

### Authentication
- `POST /api/auth/login` - Login and get JWT token
- `GET /api/auth/me` - Get current user info

### Tuition
- `GET /api/tuition/lookup?studentId={mssv}` - Lookup tuition by student ID (public)

### Payment
- `POST /api/payment/initiate` - Initiate payment and send OTP email
- `POST /api/payment/confirm` - Confirm payment with OTP
- `POST /api/payment/resend-otp` - Resend OTP
- `GET /api/payment/history` - Get payment history

## Payment Flow

1. **Login** → Get JWT token
2. **Lookup Tuition** → Search by student ID
3. **Initiate Payment** → System sends OTP to email
4. **Enter OTP** → Verify with 6-digit code (120s expiry)
5. **Confirm** → Process payment and update balance
6. **Confirmation Email** → Receive success notification

## Security Features

### Distributed Locking
- Redis-based locks prevent concurrent payment requests
- 30-second timeout with automatic expiration
- Retry mechanism with exponential backoff (3 attempts)

### Optimistic Locking
- JPA `@Version` annotation prevents lost updates
- Automatic conflict detection with `ObjectOptimisticLockingFailureException`
- Used in Customer, PaymentTransaction, and StudentTuition entities

### Transaction Isolation
- `SERIALIZABLE` isolation level ensures ACID properties
- Atomic payment processing (all-or-nothing)
- Automatic rollback on errors

## Configuration

### Backend (`application.yml`)

```yaml
app:
  otp:
    ttlSeconds: 120      # OTP expiration
    length: 6            # OTP digits
    maxAttempts: 5        # Max failed attempts
  security:
    jwtExpirationMinutes: 60
```

### Frontend

Environment variable: `NEXT_PUBLIC_API_BASE` (default: `http://localhost:8080`)

## Testing

### Postman Collection

Import `iBanking_Postman_Collection.json` to test all endpoints.

### Manual Testing

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "523H0054", "password": "pass123"}'

# Lookup tuition (public endpoint)
curl http://localhost:8080/api/tuition/lookup?studentId=523H0054

# Initiate payment (requires JWT token)
curl -X POST http://localhost:8080/api/payment/initiate \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"studentId": "523H0054"}'
```

## Database Schema

- **customers**: User accounts with balance and version
- **student_tuition**: Tuition records with paid status
- **payment_transactions**: Transaction history with status tracking

All entities use `@Version` for optimistic locking.

## Docker Services

- **backend**: Spring Boot application (port 8080)
- **frontend**: Next.js application (port 3000)
- **postgres**: PostgreSQL database (port 55432)
- **redis**: Redis cache (port 6379)
- **mailpit**: Email testing service (SMTP: 1025, UI: 8025)

## Notes

- OTP is stored in Redis with TTL
- Payment transactions use distributed locks to prevent duplicates
- All payment operations are wrapped in serializable transactions
- Email sent to both Gmail (real) and MailPit (testing)

## Troubleshooting

**OTP not received:**
- Check MailPit UI: http://localhost:8025
- View backend logs: `docker-compose logs backend`

**Payment fails:**
- Verify account balance
- Check transaction status in history
- Ensure student ID exists and tuition is unpaid

**Concurrency issues:**
- Check Redis connection
- Monitor lock keys: `docker exec ibanking_redis redis-cli KEYS "lock:*"`

## Documentation

- **Concurrency Details**: See `CONCURRENCY_AND_SECURITY_EXPLAINED.md`
- **API Collection**: `iBanking_Postman_Collection.json`

## License

MIT License

## Author

Bao Minh Nguyen - [iannwendy](https://github.com/iannwendy)
