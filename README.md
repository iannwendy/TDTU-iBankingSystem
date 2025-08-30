# iBanking Tuition Payment System

Hệ thống thanh toán học phí trực tuyến với xác thực OTP qua email, được xây dựng bằng Spring Boot và Next.js.

## 🚀 Tính năng chính

### 🔐 Xác thực và Bảo mật
- Đăng nhập bằng username/password
- JWT token authentication
- Xác thực giao dịch bằng OTP 6 chữ số
- OTP có thời hạn 120 giây (2 phút)
- Giới hạn số lần nhập OTP sai (5 lần)

### 💳 Quản lý Giao dịch
- Tìm kiếm học phí theo mã số sinh viên
- Kiểm tra số dư tài khoản
- Xử lý thanh toán học phí
- Lịch sử giao dịch chi tiết
- Trạng thái giao dịch real-time

### 📧 Thông báo Email
- Email OTP xác thực giao dịch
- Email xác nhận giao dịch thành công
- Hỗ trợ HTML email đẹp mắt
- Fallback email text đơn giản

### 🎨 Giao diện người dùng
- Responsive design cho mobile và desktop
- Dark theme hiện đại
- OTP popup với countdown timer
- Auto-focus và paste support cho OTP input
- Toast notifications

## 🏗️ Cấu trúc dự án

```
SOA_iBankingSystem/
├── backend/                    # Spring Boot Backend (Java 21)
│   ├── src/main/java/
│   │   └── com/ibanking/tuition/
│   │       ├── auth/          # Authentication & Authorization
│   │       ├── config/        # Configuration & Security
│   │       ├── payment/       # Payment processing & OTP
│   │       ├── tuition/       # Tuition management
│   │       ├── user/          # User management
│   │       └── security/      # JWT & Security
│   ├── src/main/resources/
│   │   └── application.yml    # Configuration
│   ├── Dockerfile             # Multi-stage Docker build
│   ├── pom.xml                # Maven configuration
│   ├── mvnw                   # Maven wrapper
│   └── .mvn/                  # Maven wrapper config
├── frontend/                   # Next.js Frontend
│   ├── app/
│   │   ├── components/        # React components
│   │   │   └── OtpPopup.tsx  # OTP verification popup
│   │   ├── page.tsx          # Main application page
│   │   ├── layout.tsx        # App layout
│   │   └── globals.css       # Global styles
│   ├── package.json           # Node.js dependencies
│   ├── Dockerfile             # Next.js Docker build
│   ├── tailwind.config.ts     # Tailwind CSS config
│   └── tsconfig.json          # TypeScript config
├── docker-compose.yml          # Docker services orchestration
├── .gitignore                 # Git ignore rules
└── README.md                  # Project documentation
```

## 🛠️ Cài đặt và Chạy

### Yêu cầu hệ thống
- **Java 21** (OpenJDK 21)
- **Node.js 18+** và npm
- **Docker & Docker Compose**
- **Git**

### 🐳 Chạy bằng Docker (Khuyến nghị)

#### 1. Clone repository
```bash
git clone https://github.com/iannwendy/TDTU-iBankingSystem.git
cd TDTU-iBankingSystem
```

#### 2. Khởi động toàn bộ hệ thống
```bash
docker-compose up -d
```

#### 3. Kiểm tra trạng thái
```bash
docker-compose ps
```

#### 4. Truy cập ứng dụng
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Database**: localhost:55432 (PostgreSQL)
- **Cache**: localhost:6379 (Redis)
- **Email UI**: http://localhost:8025 (MailHog)

### 🔧 Chạy thủ công

#### 1. Backend (Spring Boot)
```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```

#### 2. Frontend (Next.js)
```bash
cd frontend
npm install
npm run dev
```

#### 3. Database (PostgreSQL)
```bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=ibanking \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:15
```

## 📡 API Endpoints

### Authentication
- `POST /api/auth/login` - Đăng nhập
- `GET /api/auth/me` - Lấy thông tin user

### Payment
- `POST /api/payment/initiate` - Khởi tạo giao dịch & gửi OTP
- `POST /api/payment/confirm` - Xác nhận OTP & hoàn tất giao dịch
- `POST /api/payment/resend-otp` - Gửi lại OTP mới
- `GET /api/payment/history` - Lịch sử giao dịch

### Tuition
- `GET /api/tuition/lookup` - Tìm kiếm học phí theo MSSV

## 🔄 Quy trình giao dịch

1. **Đăng nhập** - User đăng nhập vào hệ thống
2. **Tìm kiếm học phí** - Nhập mã số sinh viên để tra cứu
3. **Xác nhận giao dịch** - Kiểm tra thông tin và bấm xác nhận
4. **Xác thực OTP** - Nhập mã OTP từ email (120s countdown)
5. **Hoàn tất** - Hệ thống xử lý thanh toán và gửi email xác nhận

## 🔐 Tính năng OTP

### Bảo mật
- OTP 6 chữ số ngẫu nhiên
- Thời hạn 120 giây
- Giới hạn 5 lần nhập sai
- Mỗi giao dịch có OTP riêng biệt

### Giao diện
- Popup modal riêng biệt
- Countdown timer hiển thị thời gian còn lại
- Auto-focus giữa các input
- Hỗ trợ paste OTP
- Nút gửi lại OTP mới

## ⚙️ Cấu hình

### Backend (application.yml)
```yaml
app:
  otp:
    ttlSeconds: 120      # Thời hạn OTP (giây)
    length: 6            # Độ dài OTP
    maxAttempts: 5       # Số lần nhập sai tối đa
  security:
    jwtExpirationMinutes: 60  # Thời hạn JWT token
```

### Frontend
- API base URL: `NEXT_PUBLIC_API_BASE`
- Default: `http://localhost:8080`

## 🐳 Docker Configuration

### Backend Dockerfile
- Multi-stage build với Maven 3.9.6 và Java 21
- Runtime image sử dụng Eclipse Temurin 21 JRE
- Expose port 8080

### Frontend Dockerfile
- Multi-stage build với Node.js 18
- Production build với Next.js
- Expose port 3000

### Docker Compose Services
- **Backend**: Spring Boot application
- **Frontend**: Next.js application  
- **PostgreSQL**: Database
- **Redis**: Cache layer
- **MailHog**: Email testing service

## 🔒 Bảo mật

- JWT token authentication
- CORS configuration
- Input validation và sanitization
- Rate limiting cho OTP attempts
- Database transaction locks
- Secure email templates

## 📊 Monitoring & Logging

- Console logging cho email failures
- Transaction status tracking
- OTP attempt monitoring
- Error handling với fallback
- Docker container health checks

## 🚨 Troubleshooting

### OTP không nhận được
- Kiểm tra cấu hình SMTP
- Xem logs backend: `docker-compose logs backend`
- Kiểm tra MailHog UI: http://localhost:8025

### Giao dịch thất bại
- Kiểm tra số dư tài khoản
- Xác nhận thông tin sinh viên
- Kiểm tra trạng thái giao dịch

### Performance issues
- Kiểm tra Redis connection
- Monitor database performance
- Kiểm tra network latency

### Docker issues
```bash
# Restart services
docker-compose restart

# Rebuild images
docker-compose build --no-cache

# View logs
docker-compose logs -f [service_name]
```

## 🧪 Testing

### Backend Testing
```bash
cd backend
./mvnw test
```

### Frontend Testing
```bash
cd frontend
npm test
```

### Integration Testing
```bash
docker-compose -f docker-compose.test.yml up --abort-on-container-exit
```

## 📈 Performance

- **Backend**: Java 21 với Spring Boot 3.x
- **Frontend**: Next.js 14 với React 18
- **Database**: PostgreSQL với connection pooling
- **Cache**: Redis cho session và OTP storage
- **Build**: Multi-stage Docker builds tối ưu

## 🔄 CI/CD

- **Build**: Maven wrapper + npm scripts
- **Containerization**: Multi-stage Docker builds
- **Orchestration**: Docker Compose
- **Deployment**: Ready for Kubernetes/Cloud deployment

## 🤝 Đóng góp

1. Fork repository
2. Tạo feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Tạo Pull Request

## 📝 Changelog

### v1.0.0 (Current)
- ✅ Spring Boot backend với Java 21
- ✅ Next.js frontend với TypeScript
- ✅ JWT authentication
- ✅ OTP email verification
- ✅ Docker deployment
- ✅ PostgreSQL + Redis
- ✅ Maven wrapper
- ✅ Multi-stage Docker builds

## 📄 License

MIT License - xem file LICENSE để biết thêm chi tiết.

## 👥 Tác giả

- **Bao Minh Nguyen** - [iannwendy](https://github.com/iannwendy)

## 🙏 Cảm ơn

- Spring Boot team
- Next.js team
- Docker community
- Open source contributors
