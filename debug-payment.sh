#!/bin/bash

echo "=== Debugging Payment Issue ==="

echo "1. Checking if you're logged in with a valid user..."
echo "Please check in your browser:"
echo "   - Open Developer Tools (F12)"
echo "   - Go to Application/Storage tab"
echo "   - Check localStorage for 'auth' key"
echo "   - Verify the token exists and is not null"

echo ""
echo "2. Testing with known working credentials..."

# Test with bob user
echo "Testing login with bob user..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password"}')

echo "Login response: $LOGIN_RESPONSE"

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "Token: $TOKEN"

if [ -z "$TOKEN" ]; then
    echo "❌ Failed to get token"
    exit 1
fi

echo ""
echo "3. Testing payment initiate with bob user..."

# Test payment initiate
INITIATE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/payment/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"studentId":"523H0002"}')

echo "Initiate response: $INITIATE_RESPONSE"

echo ""
echo "4. Checking available students for payment..."
docker exec ibanking_postgres psql -U ibanking -d ibanking -c "SELECT student_id, semester, amount, paid FROM student_tuitions WHERE paid = false LIMIT 5;"

echo ""
echo "5. If you're still getting errors, try:"
echo "   - Logout and login again"
echo "   - Clear browser cache and localStorage"
echo "   - Use incognito/private mode"
echo "   - Check browser console for JavaScript errors"
