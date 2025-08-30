# iBanking Tuition Payment System

Hệ thống thanh toán học phí trực tuyến với xác thực OTP qua email.

## Tính năng chính

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

## Cấu trúc dự án

```
SOA_iBankingSystem/
├── backend/                    # Spring Boot Backend
│   ├── src/main/java/
│   │   └── com/ibanking/tuition/
│   │       ├── auth/          # Authentication & Authorization
│   │       ├── config/        # Configuration & Security
│   │       ├── payment/       # Payment processing & OTP
│   │       ├── tuition/       # Tuition management
│   │       ├── user/          # User management
│   │       └── security/      # JWT & Security
│   └── src/main/resources/
│       └── application.yml    # Configuration
├── frontend/                   # Next.js Frontend
│   ├── app/
│   │   ├── components/        # React components
│   │   │   └── OtpPopup.tsx  # OTP verification popup
│   │   ├── page.tsx          # Main application page
│   │   └── globals.css       # Global styles
│   └── package.json
└── docker-compose.yml         # Docker services
```

## Cài đặt và Chạy

### Yêu cầu hệ thống
- Java 17+
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL
- Redis

### 1. Khởi động Infrastructure
```bash
docker-compose up -d
```

### 2. Chạy Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 3. Chạy Frontend
```bash
cd frontend
npm install
npm run dev
```

### 4. Truy cập ứng dụng
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080

## API Endpoints

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

## Quy trình giao dịch

1. **Đăng nhập** - User đăng nhập vào hệ thống
2. **Tìm kiếm học phí** - Nhập mã số sinh viên để tra cứu
3. **Xác nhận giao dịch** - Kiểm tra thông tin và bấm xác nhận
4. **Xác thực OTP** - Nhập mã OTP từ email (120s countdown)
5. **Hoàn tất** - Hệ thống xử lý thanh toán và gửi email xác nhận

## Tính năng OTP

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

## Cấu hình

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

## Bảo mật

- JWT token authentication
- CORS configuration
- Input validation và sanitization
- Rate limiting cho OTP attempts
- Database transaction locks
- Secure email templates

## Monitoring & Logging

- Console logging cho email failures
- Transaction status tracking
- OTP attempt monitoring
- Error handling với fallback

## Troubleshooting

### OTP không nhận được
- Kiểm tra cấu hình SMTP
- Xem logs backend
- Kiểm tra spam folder

### Giao dịch thất bại
- Kiểm tra số dư tài khoản
- Xác nhận thông tin sinh viên
- Kiểm tra trạng thái giao dịch

### Performance issues
- Kiểm tra Redis connection
- Monitor database performance
- Kiểm tra network latency

## Đóng góp

1. Fork repository
2. Tạo feature branch
3. Commit changes
4. Push to branch
5. Tạo Pull Request

## License

MIT License - xem file LICENSE để biết thêm chi tiết.
