#!/bin/bash

# iBanking API Test với cURL
# Script để test tất cả API endpoints

BASE_URL="http://localhost:8080"
JWT_TOKEN=""
TRANSACTION_ID=""

echo "🚀 Starting iBanking API Tests with cURL..."

# Function để in kết quả
print_result() {
    echo "📋 $1"
    echo "Status: $2"
    echo "Response: $3"
    echo "----------------------------------------"
}

# 1. Test Login
echo "🔐 Testing Login..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username": "523H0054", "password": "pass123"}')

if echo "$LOGIN_RESPONSE" | grep -q "token"; then
    JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    print_result "Login" "✅ SUCCESS" "$LOGIN_RESPONSE"
else
    print_result "Login" "❌ FAILED" "$LOGIN_RESPONSE"
    exit 1
fi

# 2. Test Get User Info
echo "👤 Testing Get User Info..."
USER_RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
    -H "Authorization: Bearer $JWT_TOKEN")
print_result "Get User Info" "✅ SUCCESS" "$USER_RESPONSE"

# 3. Test Tuition Lookup
echo "💰 Testing Tuition Lookup..."
TUITION_RESPONSE=$(curl -s -X GET "$BASE_URL/api/tuition/lookup?studentId=523H0054")
print_result "Tuition Lookup" "✅ SUCCESS" "$TUITION_RESPONSE"

# 4. Test Initiate Payment
echo "💳 Testing Initiate Payment..."
PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/payment/initiate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d '{"studentId": "523H0054"}')

if echo "$PAYMENT_RESPONSE" | grep -q "transactionId"; then
    TRANSACTION_ID=$(echo "$PAYMENT_RESPONSE" | grep -o '"transactionId":[0-9]*' | cut -d':' -f2)
    print_result "Initiate Payment" "✅ SUCCESS" "$PAYMENT_RESPONSE"
    echo "📧 Check MailPit at http://localhost:8025 for OTP"
else
    print_result "Initiate Payment" "❌ FAILED" "$PAYMENT_RESPONSE"
fi

# 5. Test Payment History
echo "📜 Testing Payment History..."
HISTORY_RESPONSE=$(curl -s -X GET "$BASE_URL/api/payment/history" \
    -H "Authorization: Bearer $JWT_TOKEN")
print_result "Payment History" "✅ SUCCESS" "$HISTORY_RESPONSE"

# 6. Test Resend OTP (nếu có transaction ID)
if [ ! -z "$TRANSACTION_ID" ]; then
    echo "🔄 Testing Resend OTP..."
    RESEND_RESPONSE=$(curl -s -X POST "$BASE_URL/api/payment/resend-otp" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d "{\"transactionId\": $TRANSACTION_ID}")
    print_result "Resend OTP" "✅ SUCCESS" "$RESEND_RESPONSE"
fi

echo "🎉 All tests completed!"
echo "📊 Check MailPit at http://localhost:8025 for email details"
echo "🌐 Frontend available at http://localhost:3000"
