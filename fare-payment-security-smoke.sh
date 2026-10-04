#!/usr/bin/env bash

set -u
umask 077

ACCOUNT_URL="http://localhost:8081"
DRIVER_URL="http://localhost:8082"
RIDE_URL="http://localhost:8083"
FARE_URL="http://localhost:8084"

for tool in curl jq; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: required command '$tool' is not installed." >&2
    exit 1
  fi
done

TMP_DIR="$(mktemp -d /tmp/fare-payment-security-smoke.XXXXXX)" || exit 1
RESPONSE_BODY="${TMP_DIR}/response.json"
RESPONSE_HEADERS="${TMP_DIR}/headers.txt"
REQUEST_BODY="${TMP_DIR}/request.json"
trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM

PASSED=0
FAILED=0
SKIPPED=0

JWT_RESULT="SKIPPED"
FARE_ESTIMATE_RESULT="SKIPPED"
FINAL_FARE_RESULT="SKIPPED"
PAYMENT_CREATE_RESULT="SKIPPED"
PAYMENT_OWNERSHIP_RESULT="SKIPPED"
PAYMENT_LIFECYCLE_RESULT="SKIPPED"
INTERNAL_AUTH_RESULT="SKIPPED"
RIDE_REGRESSION_RESULT="SKIPPED"
DRIVER_AVAILABILITY_RESULT="SKIPPED"
SWAGGER_RESULT="SKIPPED"

HTTP_STATUS=""
PREPARED_RIDE_ID=""

update_category() {
  local category="$1"
  local result="$2"
  local current

  case "$category" in
    jwt) current="$JWT_RESULT" ;;
    fare-estimate) current="$FARE_ESTIMATE_RESULT" ;;
    final-fare) current="$FINAL_FARE_RESULT" ;;
    payment-create) current="$PAYMENT_CREATE_RESULT" ;;
    payment-ownership) current="$PAYMENT_OWNERSHIP_RESULT" ;;
    payment-lifecycle) current="$PAYMENT_LIFECYCLE_RESULT" ;;
    internal-auth) current="$INTERNAL_AUTH_RESULT" ;;
    ride-regression) current="$RIDE_REGRESSION_RESULT" ;;
    driver-availability) current="$DRIVER_AVAILABILITY_RESULT" ;;
    swagger) current="$SWAGGER_RESULT" ;;
    *) return ;;
  esac

  if [[ "$result" == "FAIL" ]]; then
    current="FAIL"
  elif [[ "$result" == "PASS" && "$current" != "FAIL" ]]; then
    current="PASS"
  fi

  case "$category" in
    jwt) JWT_RESULT="$current" ;;
    fare-estimate) FARE_ESTIMATE_RESULT="$current" ;;
    final-fare) FINAL_FARE_RESULT="$current" ;;
    payment-create) PAYMENT_CREATE_RESULT="$current" ;;
    payment-ownership) PAYMENT_OWNERSHIP_RESULT="$current" ;;
    payment-lifecycle) PAYMENT_LIFECYCLE_RESULT="$current" ;;
    internal-auth) INTERNAL_AUTH_RESULT="$current" ;;
    ride-regression) RIDE_REGRESSION_RESULT="$current" ;;
    driver-availability) DRIVER_AVAILABILITY_RESULT="$current" ;;
    swagger) SWAGGER_RESULT="$current" ;;
  esac
}

report_test() {
  local result="$1"
  local number="$2"
  local name="$3"
  local expected="$4"
  local actual="$5"
  shift 5

  case "$result" in
    PASS) PASSED=$((PASSED + 1)) ;;
    FAIL) FAILED=$((FAILED + 1)) ;;
    SKIP) SKIPPED=$((SKIPPED + 1)) ;;
  esac

  printf '%s | %s | %s | expected=%s actual=%s\n' \
    "$result" "$number" "$name" "$expected" "$actual"

  local category
  for category in "$@"; do
    update_category "$category" "$result"
  done
}

request() {
  local method="$1"
  local url="$2"
  local token="${3:-}"
  local payload="${4:-}"
  local -a args

  : > "$RESPONSE_BODY"
  : > "$RESPONSE_HEADERS"
  args=(
    curl --silent --show-error
    --connect-timeout 5 --max-time 30
    --request "$method"
    --output "$RESPONSE_BODY"
    --dump-header "$RESPONSE_HEADERS"
    --write-out '%{http_code}'
    --header 'Accept: application/json'
  )

  if [[ -n "$token" ]]; then
    args+=(--header "Authorization: Bearer ${token}")
  fi

  if [[ -n "$payload" ]]; then
    printf '%s' "$payload" > "$REQUEST_BODY"
    args+=(--header 'Content-Type: application/json' --data-binary "@${REQUEST_BODY}")
  fi

  HTTP_STATUS="$("${args[@]}" "$url")"
  if [[ $? -ne 0 ]]; then
    HTTP_STATUS="curl-error"
    return 1
  fi
  return 0
}

require_status() {
  local expected="$1"
  local operation="$2"
  if [[ "$HTTP_STATUS" != "$expected" ]]; then
    echo "ERROR: setup operation '${operation}' expected ${expected}, actual ${HTTP_STATUS}." >&2
    exit 1
  fi
}

json_value() {
  jq -er "$1" "$RESPONSE_BODY" 2>/dev/null
}

check_services() {
  local url
  for url in "$ACCOUNT_URL" "$DRIVER_URL" "$RIDE_URL" "$FARE_URL"; do
    if ! curl --silent --show-error --connect-timeout 3 --max-time 5 \
      --output /dev/null "$url"; then
      echo "ERROR: required service is unavailable at ${url}." >&2
      exit 1
    fi
  done
}

register_and_login() {
  local full_name="$1"
  local email="$2"
  local phone="$3"
  local role="$4"
  local id_var="$5"
  local token_var="$6"
  local payload account_id access_token

  payload="$(printf \
    '{"fullName":"%s","email":"%s","phone":"%s","password":"%s","role":"%s"}' \
    "$full_name" "$email" "$phone" "$TEST_PASSWORD" "$role")"
  request POST "${ACCOUNT_URL}/api/auth/register" "" "$payload"
  require_status 201 "register ${role} account"
  account_id="$(json_value '.id')" || {
    echo "ERROR: registration response did not contain an account ID." >&2
    exit 1
  }

  payload="$(printf '{"email":"%s","password":"%s"}' "$email" "$TEST_PASSWORD")"
  request POST "${ACCOUNT_URL}/api/auth/login" "" "$payload"
  require_status 200 "login ${role} account"
  access_token="$(json_value '.accessToken')" || {
    echo "ERROR: login response did not contain an access token." >&2
    exit 1
  }

  printf -v "$id_var" '%s' "$account_id"
  printf -v "$token_var" '%s' "$access_token"
}

prepare_ride() {
  local passenger_id="$1"
  local passenger_token="$2"
  local driver_token="$3"
  local payload assigned_driver

  payload="$(printf \
    '{"passengerId":"%s","pickupLocation":"Colombo Fort","destinationLocation":"Bambalapitiya","serviceArea":"%s"}' \
    "$passenger_id" "$SERVICE_AREA")"
  request POST "${RIDE_URL}/api/rides" "$passenger_token" "$payload"
  [[ "$HTTP_STATUS" == "201" ]] || return 1
  PREPARED_RIDE_ID="$(json_value '.id')" || return 1

  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/assign" "$passenger_token"
  [[ "$HTTP_STATUS" == "200" ]] || return 1
  assigned_driver="$(json_value '.driverId')" || return 1
  [[ "$assigned_driver" == "$DRIVER_PROFILE_ID" ]] || return 1

  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/accept" "$driver_token"
  [[ "$HTTP_STATUS" == "200" ]] || return 1

  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/start" "$driver_token"
  [[ "$HTTP_STATUS" == "200" ]] || return 1
  return 0
}

check_services

STAMP="$(date +%s)-$$"
PHONE_SUFFIX="$(date +%s | tail -c 8)"
TEST_PASSWORD='SmokeTest-Only-42!'
SERVICE_AREA="SmokeArea-${STAMP}"
LICENSE_NUMBER="SMOKE-LIC-${STAMP}"
REGISTRATION_NUMBER="SMOKE-CAR-${STAMP}"

PASSENGER1_EMAIL="smoke-passenger1-${STAMP}@example.test"
PASSENGER2_EMAIL="smoke-passenger2-${STAMP}@example.test"
DRIVER1_EMAIL="smoke-driver1-${STAMP}@example.test"

PASSENGER1_ID=""
PASSENGER1_TOKEN=""
PASSENGER2_ID=""
PASSENGER2_TOKEN=""
DRIVER1_ID=""
DRIVER1_TOKEN=""

register_and_login "Smoke Passenger One" "$PASSENGER1_EMAIL" "+94710${PHONE_SUFFIX}1" \
  PASSENGER PASSENGER1_ID PASSENGER1_TOKEN
register_and_login "Smoke Passenger Two" "$PASSENGER2_EMAIL" "+94710${PHONE_SUFFIX}2" \
  PASSENGER PASSENGER2_ID PASSENGER2_TOKEN
register_and_login "Smoke Driver One" "$DRIVER1_EMAIL" "+94710${PHONE_SUFFIX}3" \
  DRIVER DRIVER1_ID DRIVER1_TOKEN

payload="$(printf '{"accountId":"%s","licenseNumber":"%s","serviceArea":"%s"}' \
  "$DRIVER1_ID" "$LICENSE_NUMBER" "$SERVICE_AREA")"
request POST "${DRIVER_URL}/api/drivers" "$DRIVER1_TOKEN" "$payload"
require_status 201 "create driver profile"
DRIVER_PROFILE_ID="$(json_value '.id')" || {
  echo "ERROR: driver response did not contain a profile ID." >&2
  exit 1
}

request PATCH "${DRIVER_URL}/api/drivers/${DRIVER_PROFILE_ID}/location" "$DRIVER1_TOKEN" \
  '{"latitude":6.9271,"longitude":79.8612}'
require_status 200 "update driver location"

request PATCH "${DRIVER_URL}/api/drivers/${DRIVER_PROFILE_ID}/availability" "$DRIVER1_TOKEN" \
  '{"availabilityStatus":"AVAILABLE"}'
require_status 200 "update driver availability"

payload="$(printf \
  '{"driverId":"%s","registrationNumber":"%s","make":"Toyota","model":"Aqua","color":"White","vehicleType":"CAR"}' \
  "$DRIVER_PROFILE_ID" "$REGISTRATION_NUMBER")"
request POST "${DRIVER_URL}/api/vehicles" "$DRIVER1_TOKEN" "$payload"
require_status 201 "create driver vehicle"

ESTIMATE_PAYLOAD='{"pickupLocation":"Colombo Fort","destinationLocation":"Bambalapitiya","distanceKm":12.5}'
FINAL_FARE_PAYLOAD="$(printf '{"rideId":"direct-smoke-%s","distanceKm":12.5}' "$STAMP")"
PAYMENT_PAYLOAD="$(printf \
  '{"rideId":"direct-payment-%s","passengerId":"%s","amount":1150.00,"method":"CARD"}' \
  "$STAMP" "$PASSENGER1_ID")"

request POST "${FARE_URL}/api/fares/estimate" "" "$ESTIMATE_PAYLOAD"
if [[ "$HTTP_STATUS" == "401" ]]; then
  report_test PASS 1 "Estimate without auth" 401 "$HTTP_STATUS" jwt fare-estimate
else
  report_test FAIL 1 "Estimate without auth" 401 "$HTTP_STATUS" jwt fare-estimate
fi

request POST "${FARE_URL}/api/fares/estimate" "$PASSENGER1_TOKEN" "$ESTIMATE_PAYLOAD"
ESTIMATE_ID=""
if [[ "$HTTP_STATUS" == "201" ]] && ESTIMATE_ID="$(json_value '.id')"; then
  report_test PASS 2 "Passenger creates estimate" 201 "$HTTP_STATUS" fare-estimate
else
  report_test FAIL 2 "Passenger creates estimate" 201 "${HTTP_STATUS}-invalid-response" fare-estimate
fi

request POST "${FARE_URL}/api/fares/estimate" "$DRIVER1_TOKEN" "$ESTIMATE_PAYLOAD"
if [[ "$HTTP_STATUS" == "201" ]]; then
  report_test PASS 3 "Driver creates estimate" 201 "$HTTP_STATUS" fare-estimate
else
  report_test FAIL 3 "Driver creates estimate" 201 "$HTTP_STATUS" fare-estimate
fi

if [[ -n "$ESTIMATE_ID" ]]; then
  request GET "${FARE_URL}/api/fares/estimates/${ESTIMATE_ID}" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e \
    '.distanceKm == 12.5 and (.currency | type == "string" and length > 0) and .estimatedFare > 0' \
    "$RESPONSE_BODY" >/dev/null 2>&1; then
    report_test PASS 4 "Passenger retrieves estimate" 200 "$HTTP_STATUS" fare-estimate
  else
    report_test FAIL 4 "Passenger retrieves estimate" 200 "${HTTP_STATUS}-invalid-response" fare-estimate
  fi
else
  report_test SKIP 4 "Passenger retrieves estimate" 200 "missing-estimate-id" fare-estimate
fi

request POST "${FARE_URL}/api/fares/final" "" "$FINAL_FARE_PAYLOAD"
[[ "$HTTP_STATUS" == "401" ]] && result=PASS || result=FAIL
report_test "$result" 5 "Final fare create without auth" 401 "$HTTP_STATUS" final-fare

request POST "${FARE_URL}/api/fares/final" "$PASSENGER1_TOKEN" "$FINAL_FARE_PAYLOAD"
[[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
report_test "$result" 6 "Passenger cannot create final fare" 403 "$HTTP_STATUS" final-fare

request POST "${FARE_URL}/api/fares/final" "$DRIVER1_TOKEN" "$FINAL_FARE_PAYLOAD"
[[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
report_test "$result" 7 "Driver cannot create final fare" 403 "$HTTP_STATUS" final-fare

request POST "${FARE_URL}/api/payments" "" "$PAYMENT_PAYLOAD"
[[ "$HTTP_STATUS" == "401" ]] && result=PASS || result=FAIL
report_test "$result" 8 "Payment create without auth" 401 "$HTTP_STATUS" payment-create

request POST "${FARE_URL}/api/payments" "$PASSENGER1_TOKEN" "$PAYMENT_PAYLOAD"
[[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
report_test "$result" 9 "Passenger cannot create payment" 403 "$HTTP_STATUS" payment-create

request POST "${FARE_URL}/api/payments" "$DRIVER1_TOKEN" "$PAYMENT_PAYLOAD"
[[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
report_test "$result" 10 "Driver cannot create payment" 403 "$HTTP_STATUS" payment-create

RIDE1_ID=""
PAYMENT1_ID=""
PAYMENT1_STATUS=""
if prepare_ride "$PASSENGER1_ID" "$PASSENGER1_TOKEN" "$DRIVER1_TOKEN"; then
  RIDE1_ID="$PREPARED_RIDE_ID"
  request POST "${RIDE_URL}/api/rides/${RIDE1_ID}/complete-with-payment" "$DRIVER1_TOKEN" \
    '{"distanceKm":12.5,"paymentMethod":"CARD"}'
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e \
    '.status == "COMPLETED" and .finalFare > 0 and (.paymentId | type == "string" and length > 0)' \
    "$RESPONSE_BODY" >/dev/null 2>&1; then
    PAYMENT1_ID="$(json_value '.paymentId')"
    report_test PASS 11 "Ride complete-with-payment internal auth" 200 "$HTTP_STATUS" \
      internal-auth ride-regression
  else
    report_test FAIL 11 "Ride complete-with-payment internal auth" 200 \
      "${HTTP_STATUS}-invalid-response" internal-auth ride-regression
  fi
else
  report_test FAIL 11 "Ride complete-with-payment internal auth" 200 "ride-setup-failed" \
    internal-auth ride-regression
fi

if [[ -n "$RIDE1_ID" ]]; then
  request GET "${FARE_URL}/api/fares/final/ride/${RIDE1_ID}" "$PASSENGER1_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 12 "Passenger direct final fare retrieval" 403 "$HTTP_STATUS" final-fare

  request GET "${FARE_URL}/api/fares/final/ride/${RIDE1_ID}" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 13 "Driver direct final fare retrieval" 403 "$HTTP_STATUS" final-fare

  request GET "${FARE_URL}/api/fares/final/ride/${RIDE1_ID}"
  [[ "$HTTP_STATUS" == "401" ]] && result=PASS || result=FAIL
  report_test "$result" 14 "Final fare retrieval without auth" 401 "$HTTP_STATUS" final-fare
else
  report_test SKIP 12 "Passenger direct final fare retrieval" 403 "missing-ride-id" final-fare
  report_test SKIP 13 "Driver direct final fare retrieval" 403 "missing-ride-id" final-fare
  report_test SKIP 14 "Final fare retrieval without auth" 401 "missing-ride-id" final-fare
fi

if [[ -n "$PAYMENT1_ID" ]]; then
  request GET "${FARE_URL}/api/payments/${PAYMENT1_ID}" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e \
    --arg passenger "$PASSENGER1_ID" --arg ride "$RIDE1_ID" \
    '.passengerId == $passenger and .rideId == $ride' "$RESPONSE_BODY" >/dev/null 2>&1; then
    PAYMENT1_STATUS="$(json_value '.status')"
    report_test PASS 15 "Payment owner GET by payment ID" 200 "$HTTP_STATUS" payment-ownership
  else
    report_test FAIL 15 "Payment owner GET by payment ID" 200 \
      "${HTTP_STATUS}-invalid-response" payment-ownership
  fi

  request GET "${FARE_URL}/api/payments/ride/${RIDE1_ID}" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e --arg payment "$PAYMENT1_ID" \
    '.id == $payment' "$RESPONSE_BODY" >/dev/null 2>&1; then
    report_test PASS 16 "Payment owner GET by ride ID" 200 "$HTTP_STATUS" payment-ownership
  else
    report_test FAIL 16 "Payment owner GET by ride ID" 200 \
      "${HTTP_STATUS}-invalid-response" payment-ownership
  fi

  request GET "${FARE_URL}/api/payments/${PAYMENT1_ID}" "$PASSENGER2_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 17 "Other passenger GET payment" 403 "$HTTP_STATUS" payment-ownership

  request GET "${FARE_URL}/api/payments/${PAYMENT1_ID}" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 18 "Driver GET payment" 403 "$HTTP_STATUS" payment-ownership

  request GET "${FARE_URL}/api/payments/${PAYMENT1_ID}"
  [[ "$HTTP_STATUS" == "401" ]] && result=PASS || result=FAIL
  report_test "$result" 19 "Payment GET without auth" 401 "$HTTP_STATUS" payment-ownership
else
  for entry in \
    '15|Payment owner GET by payment ID|200' \
    '16|Payment owner GET by ride ID|200' \
    '17|Other passenger GET payment|403' \
    '18|Driver GET payment|403' \
    '19|Payment GET without auth|401'; do
    IFS='|' read -r number name expected <<< "$entry"
    report_test SKIP "$number" "$name" "$expected" "missing-payment-id" payment-ownership
  done
fi

CAN_REPEAT_COMPLETE=0
if [[ "$PAYMENT1_STATUS" == "PENDING" ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT1_ID}/complete" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e '.status == "COMPLETED"' \
    "$RESPONSE_BODY" >/dev/null 2>&1; then
    report_test PASS 20 "Owner completes payment" 200 "$HTTP_STATUS" payment-lifecycle
    CAN_REPEAT_COMPLETE=1
  else
    report_test FAIL 20 "Owner completes payment" 200 \
      "${HTTP_STATUS}-invalid-response" payment-lifecycle
  fi
elif [[ "$PAYMENT1_STATUS" == "COMPLETED" ]]; then
  report_test SKIP 20 "Owner completes payment" 200 "already-completed" payment-lifecycle
  CAN_REPEAT_COMPLETE=1
else
  report_test SKIP 20 "Owner completes payment" 200 "no-pending-payment" payment-lifecycle
fi

if [[ "$CAN_REPEAT_COMPLETE" == "1" ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT1_ID}/complete" "$PASSENGER1_TOKEN"
  [[ "$HTTP_STATUS" == "409" ]] && result=PASS || result=FAIL
  report_test "$result" 21 "Repeat payment completion" 409 "$HTTP_STATUS" payment-lifecycle
else
  report_test SKIP 21 "Repeat payment completion" 409 "completion-not-performed" payment-lifecycle
fi

if [[ -n "$PAYMENT1_ID" ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT1_ID}/complete" "$PASSENGER2_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 22 "Unrelated passenger attempts completion" 403 "$HTTP_STATUS" \
    payment-lifecycle
else
  report_test SKIP 22 "Unrelated passenger attempts completion" 403 "missing-payment-id" \
    payment-lifecycle
fi

RIDE2_ID=""
PAYMENT2_ID=""
PAYMENT2_STATUS=""
if prepare_ride "$PASSENGER1_ID" "$PASSENGER1_TOKEN" "$DRIVER1_TOKEN"; then
  RIDE2_ID="$PREPARED_RIDE_ID"
  request POST "${RIDE_URL}/api/rides/${RIDE2_ID}/complete-with-payment" "$DRIVER1_TOKEN" \
    '{"distanceKm":8.25,"paymentMethod":"CASH"}'
  if [[ "$HTTP_STATUS" == "200" ]]; then
    PAYMENT2_ID="$(json_value '.paymentId' || true)"
    if [[ -n "$PAYMENT2_ID" ]]; then
      request GET "${FARE_URL}/api/payments/${PAYMENT2_ID}" "$PASSENGER1_TOKEN"
      if [[ "$HTTP_STATUS" == "200" ]]; then
        PAYMENT2_STATUS="$(json_value '.status' || true)"
      fi
    fi
  fi
fi

if [[ "$PAYMENT2_STATUS" == "PENDING" ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT2_ID}/fail" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e '.status == "FAILED"' \
    "$RESPONSE_BODY" >/dev/null 2>&1; then
    report_test PASS 23 "Owner fails pending payment" 200 "$HTTP_STATUS" payment-lifecycle
    request POST "${FARE_URL}/api/payments/${PAYMENT2_ID}/fail" "$PASSENGER1_TOKEN"
    [[ "$HTTP_STATUS" == "409" ]] && result=PASS || result=FAIL
    report_test "$result" 24 "Repeat payment failure" 409 "$HTTP_STATUS" payment-lifecycle
  else
    report_test FAIL 23 "Owner fails pending payment" 200 \
      "${HTTP_STATUS}-invalid-response" payment-lifecycle
    report_test SKIP 24 "Repeat payment failure" 409 "failure-not-performed" payment-lifecycle
  fi
else
  report_test SKIP 23 "Owner fails pending payment" 200 "no-pending-payment" payment-lifecycle
  report_test SKIP 24 "Repeat payment failure" 409 "no-failed-payment" payment-lifecycle
fi

STATUS_PAYMENT_ID="${PAYMENT2_ID:-$PAYMENT1_ID}"
if [[ -n "$STATUS_PAYMENT_ID" ]]; then
  request POST "${FARE_URL}/api/payments/${STATUS_PAYMENT_ID}/complete" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 25 "Driver cannot change payment status" 403 "$HTTP_STATUS" \
    payment-lifecycle

  request POST "${FARE_URL}/api/payments/${STATUS_PAYMENT_ID}/complete" "$PASSENGER2_TOKEN"
  [[ "$HTTP_STATUS" == "403" ]] && result=PASS || result=FAIL
  report_test "$result" 26 "Other passenger cannot change payment status" 403 "$HTTP_STATUS" \
    payment-lifecycle
else
  report_test SKIP 25 "Driver cannot change payment status" 403 "missing-payment-id" \
    payment-lifecycle
  report_test SKIP 26 "Other passenger cannot change payment status" 403 "missing-payment-id" \
    payment-lifecycle
fi

request GET "${FARE_URL}/api/payments/not-a-real-payment" "obviously-malformed-token"
if [[ "$HTTP_STATUS" == "401" ]] && grep -qi '^Content-Type:.*application/json' "$RESPONSE_HEADERS"; then
  report_test PASS 27 "Malformed JWT returns JSON" "401-json" "401-json" jwt
else
  report_test FAIL 27 "Malformed JWT returns JSON" "401-json" "$HTTP_STATUS" jwt
fi

request GET "${FARE_URL}/api/payments/not-a-real-payment"
if [[ "$HTTP_STATUS" == "401" ]] \
  && grep -qi '^Content-Type:.*application/json' "$RESPONSE_HEADERS" \
  && ! grep -qi '^Location:' "$RESPONSE_HEADERS" \
  && ! grep -qi '^WWW-Authenticate:[[:space:]]*Basic' "$RESPONSE_HEADERS" \
  && ! grep -Eqi '<html|login form' "$RESPONSE_BODY"; then
  report_test PASS 28 "No HTTP Basic or form login" "401-json-no-challenge" \
    "401-json-no-challenge" jwt
else
  report_test FAIL 28 "No HTTP Basic or form login" "401-json-no-challenge" "$HTTP_STATUS" jwt
fi

request GET "${FARE_URL}/v3/api-docs"
[[ "$HTTP_STATUS" == "200" ]] && result=PASS || result=FAIL
report_test "$result" 29 "Swagger API docs public" 200 "$HTTP_STATUS" swagger

request GET "${FARE_URL}/swagger-ui/index.html"
[[ "$HTTP_STATUS" == "200" ]] && result=PASS || result=FAIL
report_test "$result" 30 "Swagger UI public" 200 "$HTTP_STATUS" swagger

if [[ -n "$RIDE1_ID" && -n "$PAYMENT1_ID" ]]; then
  request GET "${RIDE_URL}/api/rides/${RIDE1_ID}" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == "200" ]] && jq -e --arg payment "$PAYMENT1_ID" \
    '.status == "COMPLETED" and .finalFare > 0 and .paymentId == $payment' \
    "$RESPONSE_BODY" >/dev/null 2>&1; then
    update_category ride-regression PASS
  else
    update_category ride-regression FAIL
  fi
else
  update_category ride-regression FAIL
fi

request GET "${DRIVER_URL}/api/drivers/${DRIVER_PROFILE_ID}" "$DRIVER1_TOKEN"
if [[ "$HTTP_STATUS" == "200" ]] && jq -e '.availabilityStatus == "AVAILABLE"' \
  "$RESPONSE_BODY" >/dev/null 2>&1; then
  update_category driver-availability PASS
else
  update_category driver-availability FAIL
fi

echo
echo "FARE & PAYMENT SECURITY LIVE SMOKE TEST"
echo
echo "Passed: ${PASSED}"
echo "Failed: ${FAILED}"
echo "Skipped: ${SKIPPED}"
echo
echo "JWT authentication: ${JWT_RESULT}"
echo "Fare estimate authorization: ${FARE_ESTIMATE_RESULT}"
echo "Final fare authorization: ${FINAL_FARE_RESULT}"
echo "Payment creation authorization: ${PAYMENT_CREATE_RESULT}"
echo "Payment ownership: ${PAYMENT_OWNERSHIP_RESULT}"
echo "Payment lifecycle authorization: ${PAYMENT_LIFECYCLE_RESULT}"
echo "Ride -> Fare internal authentication: ${INTERNAL_AUTH_RESULT}"
echo "Ride complete-with-payment regression: ${RIDE_REGRESSION_RESULT}"
echo "Driver availability regression: ${DRIVER_AVAILABILITY_RESULT}"
echo "Swagger: ${SWAGGER_RESULT}"

if [[ "$FAILED" -gt 0 \
  || "$INTERNAL_AUTH_RESULT" == "FAIL" \
  || "$RIDE_REGRESSION_RESULT" == "FAIL" \
  || "$DRIVER_AVAILABILITY_RESULT" == "FAIL" ]]; then
  exit 1
fi
