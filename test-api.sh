#!/bin/bash

# iBanking API Test Script
# Sử dụng Newman để chạy Postman collection tự động

echo "🚀 Starting iBanking API Tests..."

# Kiểm tra xem Newman đã cài đặt chưa
if ! command -v newman &> /dev/null; then
    echo "❌ Newman not found. Installing..."
    npm install -g newman
fi

# Kiểm tra xem server có chạy không
echo "🔍 Checking if server is running..."
if curl -s http://localhost:8080/api/auth/me > /dev/null 2>&1; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running. Please start with: docker-compose up -d"
    exit 1
fi

# Chạy collection với Newman
echo "🧪 Running API tests..."
newman run iBanking_Postman_Collection.json \
    --reporters cli,html \
    --reporter-html-export test-results.html \
    --delay-request 1000

echo "📊 Test results saved to test-results.html"
echo "🌐 Open http://localhost:8025 to check emails"
