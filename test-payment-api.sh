#!/bin/bash

echo "=== Testing Payment Initiate API ==="

# Test 1: Check if backend is responding
echo "1. Testing backend health..."
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer invalid-token" | head -5

echo ""
echo "2. Testing with proper login first..."

# Test 2: Login to get valid token
echo "Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}')

echo "Login response: $LOGIN_RESPONSE"

# Extract token from response
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "Extracted token: $TOKEN"

if [ -z "$TOKEN" ]; then
    echo "❌ Failed to get token from login response"
    exit 1
fi

echo ""
echo "3. Testing payment initiate with valid token..."

# Test 3: Test payment initiate
INITIATE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/payment/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"studentId":"12345678"}')

echo "Initiate response: $INITIATE_RESPONSE"

echo ""
echo "4. Checking backend logs for errors..."
docker logs ibanking_backend --tail 10 | grep -E "(ERROR|Exception|Failed)"
