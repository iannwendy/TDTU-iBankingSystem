#!/bin/bash

echo "=== Fixing Payment Issues ==="

echo "1. The main issues are:"
echo "   - JWT token is empty or malformed"
echo "   - Database constraint violation with lock_expiry"

echo ""
echo "2. Solutions:"

echo ""
echo "   A) Fix JWT Token Issue:"
echo "      - Logout and login again in the browser"
echo "      - Clear browser cache and localStorage"
echo "      - Use incognito/private mode"

echo ""
echo "   B) Fix Database Issue:"
echo "      - The lock_expiry field is being set to null"
echo "      - This is a code issue that needs to be fixed"

echo ""
echo "3. Testing with working credentials..."

# Test with bob user
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
echo "4. Testing payment initiate..."

# Test payment initiate
INITIATE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/payment/initiate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"studentId":"523H0007"}')

echo "Initiate response: $INITIATE_RESPONSE"

echo ""
echo "5. If you're still getting errors:"
echo "   - Check browser console for JavaScript errors"
echo "   - Verify the token in localStorage is not null"
echo "   - Try logging in with username: 'bob', password: 'password'"
echo "   - Use student ID: '523H0007' for testing"
