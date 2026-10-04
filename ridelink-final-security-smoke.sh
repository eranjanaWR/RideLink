#!/usr/bin/env bash

set -u
umask 077

ACCOUNT_URL='http://localhost:8081'
DRIVER_URL='http://localhost:8082'
RIDE_URL='http://localhost:8083'
FARE_URL='http://localhost:8084'

for tool in curl plutil awk grep mktemp; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "PREREQUISITE FAILURE: required command '$tool' is unavailable." >&2
    exit 1
  fi
done

TMP_DIR="$(mktemp -d /tmp/ridelink-final-security-smoke.XXXXXX)" || exit 1
RESPONSE_BODY="${TMP_DIR}/response.json"
RESPONSE_HEADERS="${TMP_DIR}/response.headers"
REQUEST_BODY="${TMP_DIR}/request.json"
SENSITIVE_PATTERNS="${TMP_DIR}/sensitive-patterns.txt"
trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM

PASSED=0
FAILED=0
SKIPPED=0

ACCOUNT_RESULT='SKIPPED'
DRIVER_RESULT='SKIPPED'
VEHICLE_RESULT='SKIPPED'
RIDE_PASSENGER_RESULT='SKIPPED'
ASSIGNED_DRIVER_RESULT='SKIPPED'
RIDE_DRIVER_INTERNAL_RESULT='SKIPPED'
RIDE_FARE_INTERNAL_RESULT='SKIPPED'
FARE_RESULT='SKIPPED'
PAYMENT_OWNERSHIP_RESULT='SKIPPED'
PAYMENT_LIFECYCLE_RESULT='SKIPPED'
DRIVER_AVAILABILITY_RESULT='SKIPPED'
SWAGGER_RESULT='SKIPPED'
LOG_REVIEW_RESULT='SKIPPED'

HTTP_STATUS=''
PREPARED_RIDE_ID=''

update_category() {
  local category="$1"
  local result="$2"
  local current

  case "$category" in
    account) current="$ACCOUNT_RESULT" ;;
    driver) current="$DRIVER_RESULT" ;;
    vehicle) current="$VEHICLE_RESULT" ;;
    ride-passenger) current="$RIDE_PASSENGER_RESULT" ;;
    assigned-driver) current="$ASSIGNED_DRIVER_RESULT" ;;
    ride-driver-internal) current="$RIDE_DRIVER_INTERNAL_RESULT" ;;
    ride-fare-internal) current="$RIDE_FARE_INTERNAL_RESULT" ;;
    fare) current="$FARE_RESULT" ;;
    payment-ownership) current="$PAYMENT_OWNERSHIP_RESULT" ;;
    payment-lifecycle) current="$PAYMENT_LIFECYCLE_RESULT" ;;
    driver-availability) current="$DRIVER_AVAILABILITY_RESULT" ;;
    swagger) current="$SWAGGER_RESULT" ;;
    log-review) current="$LOG_REVIEW_RESULT" ;;
    *) return ;;
  esac

  if [[ "$result" == 'FAIL' ]]; then
    current='FAIL'
  elif [[ "$result" == 'PASS' && "$current" != 'FAIL' ]]; then
    current='PASS'
  fi

  case "$category" in
    account) ACCOUNT_RESULT="$current" ;;
    driver) DRIVER_RESULT="$current" ;;
    vehicle) VEHICLE_RESULT="$current" ;;
    ride-passenger) RIDE_PASSENGER_RESULT="$current" ;;
    assigned-driver) ASSIGNED_DRIVER_RESULT="$current" ;;
    ride-driver-internal) RIDE_DRIVER_INTERNAL_RESULT="$current" ;;
    ride-fare-internal) RIDE_FARE_INTERNAL_RESULT="$current" ;;
    fare) FARE_RESULT="$current" ;;
    payment-ownership) PAYMENT_OWNERSHIP_RESULT="$current" ;;
    payment-lifecycle) PAYMENT_LIFECYCLE_RESULT="$current" ;;
    driver-availability) DRIVER_AVAILABILITY_RESULT="$current" ;;
    swagger) SWAGGER_RESULT="$current" ;;
    log-review) LOG_REVIEW_RESULT="$current" ;;
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

report_verification() {
  local result="$1"
  local name="$2"
  local expected="$3"
  local actual="$4"
  local category="$5"
  printf '%s | verification | %s | expected=%s actual=%s\n' \
    "$result" "$name" "$expected" "$actual"
  update_category "$category" "$result"
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
    HTTP_STATUS='curl-error'
    return 1
  fi
  return 0
}

json_value() {
  plutil -extract "$1" raw -o - "$RESPONSE_BODY" 2>/dev/null
}

positive_number() {
  awk -v value="$1" 'BEGIN { exit !(value + 0 > 0) }'
}

require_setup_status() {
  local expected="$1"
  local operation="$2"
  if [[ "$HTTP_STATUS" != "$expected" ]]; then
    echo "SETUP FAILURE: ${operation}; expected=${expected} actual=${HTTP_STATUS}." >&2
    exit 1
  fi
}

require_value() {
  local value="$1"
  local description="$2"
  if [[ -z "$value" ]]; then
    echo "SETUP FAILURE: ${description} was absent from the response." >&2
    exit 1
  fi
}

check_json_unauthorized() {
  [[ "$HTTP_STATUS" == '401' ]] \
    && grep -qi '^Content-Type:.*application/json' "$RESPONSE_HEADERS"
}

register_account() {
  local full_name="$1"
  local email="$2"
  local phone="$3"
  local role="$4"
  local payload
  payload="$(printf \
    '{"fullName":"%s","email":"%s","phone":"%s","password":"%s","role":"%s"}' \
    "$full_name" "$email" "$phone" "$TEST_PASSWORD" "$role")"
  request POST "${ACCOUNT_URL}/api/auth/register" '' "$payload"
}

login_account() {
  local email="$1"
  local password="$2"
  local payload
  payload="$(printf '{"email":"%s","password":"%s"}' "$email" "$password")"
  request POST "${ACCOUNT_URL}/api/auth/login" '' "$payload"
}

prepare_ride_for_payment() {
  local destination="$1"
  local payload assigned_driver

  payload="$(printf \
    '{"passengerId":"%s","pickupLocation":"Colombo Fort","destinationLocation":"%s","serviceArea":"%s"}' \
    "$PASSENGER1_ID" "$destination" "$SERVICE_AREA")"
  request POST "${RIDE_URL}/api/rides" "$PASSENGER1_TOKEN" "$payload"
  [[ "$HTTP_STATUS" == '201' ]] || return 1
  PREPARED_RIDE_ID="$(json_value id || true)"
  [[ -n "$PREPARED_RIDE_ID" ]] || return 1

  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/assign" "$PASSENGER1_TOKEN"
  [[ "$HTTP_STATUS" == '200' ]] || return 1
  assigned_driver="$(json_value driverId || true)"
  [[ "$assigned_driver" == "$DRIVER1_PROFILE_ID" ]] || return 1

  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/accept" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == '200' ]] || return 1
  request POST "${RIDE_URL}/api/rides/${PREPARED_RIDE_ID}/start" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == '200' ]] || return 1
  return 0
}

for service in \
  "Account|${ACCOUNT_URL}" \
  "Driver|${DRIVER_URL}" \
  "Ride|${RIDE_URL}" \
  "Fare/Payment|${FARE_URL}"; do
  IFS='|' read -r service_name service_url <<< "$service"
  request GET "${service_url}/v3/api-docs"
  if [[ "$HTTP_STATUS" != '200' ]]; then
    echo "PREREQUISITE FAILURE: ${service_name} API docs expected=200 actual=${HTTP_STATUS}." >&2
    exit 1
  fi
done

STAMP="$(date +%s)-$$"
PHONE_SUFFIX="$(date +%s | tail -c 7)"
TEST_PASSWORD="Smoke-${STAMP}-Aa1!"
INVALID_PASSWORD="Invalid-${STAMP}-Aa1!"
SERVICE_AREA="SecureArea-${STAMP}"
LICENSE_NUMBER="SEC-LIC-${STAMP}"
REGISTRATION_NUMBER="SEC-CAR-${STAMP}"

PASSENGER1_EMAIL="security-p1-${STAMP}@example.test"
PASSENGER2_EMAIL="security-p2-${STAMP}@example.test"
DRIVER1_EMAIL="security-d1-${STAMP}@example.test"
DRIVER2_EMAIL="security-d2-${STAMP}@example.test"

PASSENGER1_ID=''
PASSENGER1_TOKEN=''
PASSENGER2_ID=''
PASSENGER2_TOKEN=''
DRIVER1_ID=''
DRIVER1_TOKEN=''
DRIVER2_ID=''
DRIVER2_TOKEN=''

register_account 'Security Passenger One' "$PASSENGER1_EMAIL" "+94711${PHONE_SUFFIX}1" PASSENGER
PASSENGER1_REGISTER_PAYLOAD="$(printf \
  '{"fullName":"%s","email":"%s","phone":"%s","password":"%s","role":"PASSENGER"}' \
  'Security Passenger One' "$PASSENGER1_EMAIL" "+94711${PHONE_SUFFIX}1" "$TEST_PASSWORD")"
if [[ "$HTTP_STATUS" == '201' ]]; then
  PASSENGER1_ID="$(json_value id || true)"
  report_test PASS 1 'PASSENGER registration' 201 "$HTTP_STATUS" account
else
  report_test FAIL 1 'PASSENGER registration' 201 "$HTTP_STATUS" account
fi
require_value "$PASSENGER1_ID" 'PASSENGER1 account ID'

register_account 'Security Driver One' "$DRIVER1_EMAIL" "+94711${PHONE_SUFFIX}2" DRIVER
if [[ "$HTTP_STATUS" == '201' ]]; then
  DRIVER1_ID="$(json_value id || true)"
  report_test PASS 2 'DRIVER registration' 201 "$HTTP_STATUS" account
else
  report_test FAIL 2 'DRIVER registration' 201 "$HTTP_STATUS" account
fi
require_value "$DRIVER1_ID" 'DRIVER1 account ID'

request POST "${ACCOUNT_URL}/api/auth/register" '' "$PASSENGER1_REGISTER_PAYLOAD"
[[ "$HTTP_STATUS" == '409' ]] && result=PASS || result=FAIL
report_test "$result" 3 'Duplicate registration' 409 "$HTTP_STATUS" account

register_account 'Security Passenger Two' "$PASSENGER2_EMAIL" "+94711${PHONE_SUFFIX}3" PASSENGER
require_setup_status 201 'register PASSENGER2'
PASSENGER2_ID="$(json_value id || true)"
require_value "$PASSENGER2_ID" 'PASSENGER2 account ID'

register_account 'Security Driver Two' "$DRIVER2_EMAIL" "+94711${PHONE_SUFFIX}4" DRIVER
require_setup_status 201 'register DRIVER2'
DRIVER2_ID="$(json_value id || true)"
require_value "$DRIVER2_ID" 'DRIVER2 account ID'

login_account "$PASSENGER1_EMAIL" "$TEST_PASSWORD"
if [[ "$HTTP_STATUS" == '200' ]]; then
  PASSENGER1_TOKEN="$(json_value accessToken || true)"
  report_test PASS 4 'PASSENGER login' 200 "$HTTP_STATUS" account
else
  report_test FAIL 4 'PASSENGER login' 200 "$HTTP_STATUS" account
fi
require_value "$PASSENGER1_TOKEN" 'PASSENGER1 access token'

login_account "$DRIVER1_EMAIL" "$TEST_PASSWORD"
if [[ "$HTTP_STATUS" == '200' ]]; then
  DRIVER1_TOKEN="$(json_value accessToken || true)"
  report_test PASS 5 'DRIVER login' 200 "$HTTP_STATUS" account
else
  report_test FAIL 5 'DRIVER login' 200 "$HTTP_STATUS" account
fi
require_value "$DRIVER1_TOKEN" 'DRIVER1 access token'

login_account "$PASSENGER2_EMAIL" "$TEST_PASSWORD"
require_setup_status 200 'login PASSENGER2'
PASSENGER2_TOKEN="$(json_value accessToken || true)"
require_value "$PASSENGER2_TOKEN" 'PASSENGER2 access token'

login_account "$DRIVER2_EMAIL" "$TEST_PASSWORD"
require_setup_status 200 'login DRIVER2'
DRIVER2_TOKEN="$(json_value accessToken || true)"
require_value "$DRIVER2_TOKEN" 'DRIVER2 access token'

login_account "$PASSENGER1_EMAIL" "$INVALID_PASSWORD"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 6 'Invalid login' 401 "$HTTP_STATUS" account

request GET "${ACCOUNT_URL}/api/accounts/${PASSENGER1_ID}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 7 'Owner account profile GET' 200 "$HTTP_STATUS" account

request GET "${ACCOUNT_URL}/api/accounts/${PASSENGER2_ID}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 8 'Cross-account profile GET' 403 "$HTTP_STATUS" account

DRIVER_PROFILE_PAYLOAD="$(printf \
  '{"accountId":"%s","licenseNumber":"%s","serviceArea":"%s"}' \
  "$DRIVER1_ID" "$LICENSE_NUMBER" "$SERVICE_AREA")"
request POST "${DRIVER_URL}/api/drivers" "$DRIVER1_TOKEN" "$DRIVER_PROFILE_PAYLOAD"
require_setup_status 201 'create DRIVER1 profile'
DRIVER1_PROFILE_ID="$(json_value id || true)"
require_value "$DRIVER1_PROFILE_ID" 'DRIVER1 profile ID'

request PATCH "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}/availability" "$DRIVER1_TOKEN" \
  '{"availabilityStatus":"AVAILABLE"}'
require_setup_status 200 'set DRIVER1 availability'
request PATCH "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}/location" "$DRIVER1_TOKEN" \
  '{"latitude":6.9271,"longitude":79.8612}'
require_setup_status 200 'set DRIVER1 location'

VEHICLE_PAYLOAD="$(printf \
  '{"driverId":"%s","registrationNumber":"%s","make":"Toyota","model":"Aqua","color":"White","vehicleType":"CAR"}' \
  "$DRIVER1_PROFILE_ID" "$REGISTRATION_NUMBER")"
request POST "${DRIVER_URL}/api/vehicles" "$DRIVER1_TOKEN" "$VEHICLE_PAYLOAD"
require_setup_status 201 'create DRIVER1 vehicle'
VEHICLE_ID="$(json_value id || true)"
require_value "$VEHICLE_ID" 'vehicle ID'

UNAUTHORIZED_DRIVER_PAYLOAD="$(printf \
  '{"accountId":"%s","licenseNumber":"NOAUTH-%s","serviceArea":"%s"}' \
  "$DRIVER2_ID" "$STAMP" "$SERVICE_AREA")"
request POST "${DRIVER_URL}/api/drivers" '' "$UNAUTHORIZED_DRIVER_PAYLOAD"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 9 'Unauthenticated driver create' 401 "$HTTP_STATUS" driver

PASSENGER_DRIVER_PAYLOAD="$(printf \
  '{"accountId":"%s","licenseNumber":"PASS-%s","serviceArea":"%s"}' \
  "$PASSENGER1_ID" "$STAMP" "$SERVICE_AREA")"
request POST "${DRIVER_URL}/api/drivers" "$PASSENGER1_TOKEN" "$PASSENGER_DRIVER_PAYLOAD"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 10 'Passenger driver create' 403 "$HTTP_STATUS" driver

request GET "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 11 'Owner driver profile GET' 200 "$HTTP_STATUS" driver

request GET "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}" "$DRIVER2_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 12 'Unrelated driver profile GET' 403 "$HTTP_STATUS" driver

request GET "${DRIVER_URL}/api/vehicles/${VEHICLE_ID}" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 13 'Owner vehicle GET' 200 "$HTTP_STATUS" vehicle

request GET "${DRIVER_URL}/api/vehicles/${VEHICLE_ID}" "$DRIVER2_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 14 'Unrelated driver vehicle GET' 403 "$HTTP_STATUS" vehicle

request GET "${DRIVER_URL}/api/vehicles/${VEHICLE_ID}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 15 'Passenger vehicle GET' 403 "$HTTP_STATUS" vehicle

request GET "${DRIVER_URL}/api/drivers/eligible?serviceArea=${SERVICE_AREA}"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 16 'Eligible search without auth' 401 "$HTTP_STATUS" driver

request GET "${DRIVER_URL}/api/drivers/eligible?serviceArea=${SERVICE_AREA}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 17 'Eligible search with JWT' 200 "$HTTP_STATUS" driver

RIDE_PAYLOAD="$(printf \
  '{"passengerId":"%s","pickupLocation":"Colombo Fort","destinationLocation":"Bambalapitiya","serviceArea":"%s"}' \
  "$PASSENGER1_ID" "$SERVICE_AREA")"
request POST "${RIDE_URL}/api/rides" '' "$RIDE_PAYLOAD"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 18 'Create ride without JWT' 401 "$HTTP_STATUS" ride-passenger

request POST "${RIDE_URL}/api/rides" "$PASSENGER1_TOKEN" "$RIDE_PAYLOAD"
if [[ "$HTTP_STATUS" == '201' ]]; then
  RIDE_ID="$(json_value id || true)"
  report_test PASS 19 'Passenger creates own ride' 201 "$HTTP_STATUS" ride-passenger
else
  RIDE_ID=''
  report_test FAIL 19 'Passenger creates own ride' 201 "$HTTP_STATUS" ride-passenger
fi
require_value "$RIDE_ID" 'ride ID'

CROSS_RIDE_PAYLOAD="$(printf \
  '{"passengerId":"%s","pickupLocation":"Colombo Fort","destinationLocation":"Dehiwala","serviceArea":"%s"}' \
  "$PASSENGER2_ID" "$SERVICE_AREA")"
request POST "${RIDE_URL}/api/rides" "$PASSENGER1_TOKEN" "$CROSS_RIDE_PAYLOAD"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 20 'Passenger creates ride for another passenger' 403 "$HTTP_STATUS" ride-passenger

request GET "${RIDE_URL}/api/rides/${RIDE_ID}" "$PASSENGER2_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 21 'Unrelated passenger GET ride' 403 "$HTTP_STATUS" ride-passenger

request GET "${RIDE_URL}/api/rides/${RIDE_ID}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 22 'Owner passenger GET ride' 200 "$HTTP_STATUS" ride-passenger

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/assign" "$PASSENGER1_TOKEN"
ASSIGNED_DRIVER_ID="$(json_value driverId || true)"
if [[ "$HTTP_STATUS" == '200' && "$ASSIGNED_DRIVER_ID" == "$DRIVER1_PROFILE_ID" ]]; then
  report_test PASS 23 'Owner assigns ride' '200-driver1' '200-driver1' \
    ride-passenger ride-driver-internal
else
  report_test FAIL 23 'Owner assigns ride' '200-driver1' "${HTTP_STATUS}-assignment-mismatch" \
    ride-passenger ride-driver-internal
fi

request GET "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}" "$DRIVER1_TOKEN"
AVAILABILITY="$(json_value availabilityStatus || true)"
if [[ "$HTTP_STATUS" == '200' && "$AVAILABILITY" == 'UNAVAILABLE' ]]; then
  report_verification PASS 'Driver unavailable after assignment' UNAVAILABLE UNAVAILABLE driver-availability
else
  report_verification FAIL 'Driver unavailable after assignment' UNAVAILABLE \
    "${HTTP_STATUS}-${AVAILABILITY:-unknown}" driver-availability
fi

request GET "${RIDE_URL}/api/rides/${RIDE_ID}" "$DRIVER2_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 24 'Unrelated driver GET assigned ride' 403 "$HTTP_STATUS" assigned-driver

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/accept" "$DRIVER2_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 25 'Unrelated driver accepts ride' 403 "$HTTP_STATUS" assigned-driver

request GET "${RIDE_URL}/api/rides/${RIDE_ID}" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 26 'Assigned driver GET ride' 200 "$HTTP_STATUS" assigned-driver

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/accept" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 27 'Assigned driver accepts ride' 200 "$HTTP_STATUS" assigned-driver

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/start" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
report_test "$result" 28 'Assigned driver starts ride' 200 "$HTTP_STATUS" assigned-driver

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/complete" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 29 'Passenger cannot complete ride' 403 "$HTTP_STATUS" assigned-driver

request POST "${RIDE_URL}/api/rides/${RIDE_ID}/complete-with-payment" "$DRIVER1_TOKEN" \
  '{"distanceKm":12.5,"paymentMethod":"CARD"}'
RIDE_STATUS="$(json_value status || true)"
FINAL_FARE="$(json_value finalFare || true)"
PAYMENT_ID="$(json_value paymentId || true)"
COMPLETED_AT="$(json_value completedAt || true)"
if [[ "$HTTP_STATUS" == '200' && "$RIDE_STATUS" == 'COMPLETED' \
  && -n "$PAYMENT_ID" && -n "$COMPLETED_AT" ]] && positive_number "$FINAL_FARE"; then
  report_test PASS 30 'Assigned driver completes with payment' '200-valid-result' \
    '200-valid-result' assigned-driver ride-fare-internal
else
  report_test FAIL 30 'Assigned driver completes with payment' '200-valid-result' \
    "${HTTP_STATUS}-invalid-response" assigned-driver ride-fare-internal
fi

request GET "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}" "$DRIVER1_TOKEN"
AVAILABILITY="$(json_value availabilityStatus || true)"
if [[ "$HTTP_STATUS" == '200' && "$AVAILABILITY" == 'AVAILABLE' ]]; then
  report_verification PASS 'Driver available after completion' AVAILABLE AVAILABLE driver-availability
else
  report_verification FAIL 'Driver available after completion' AVAILABLE \
    "${HTTP_STATUS}-${AVAILABILITY:-unknown}" driver-availability
fi

FARE_ESTIMATE_PAYLOAD='{"pickupLocation":"Colombo Fort","destinationLocation":"Bambalapitiya","distanceKm":12.5}'
request POST "${FARE_URL}/api/fares/estimate" '' "$FARE_ESTIMATE_PAYLOAD"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 31 'Fare estimate without JWT' 401 "$HTTP_STATUS" fare

request POST "${FARE_URL}/api/fares/estimate" "$PASSENGER1_TOKEN" "$FARE_ESTIMATE_PAYLOAD"
[[ "$HTTP_STATUS" == '201' ]] && result=PASS || result=FAIL
report_test "$result" 32 'Passenger fare estimate' 201 "$HTTP_STATUS" fare

request POST "${FARE_URL}/api/fares/estimate" "$DRIVER1_TOKEN" "$FARE_ESTIMATE_PAYLOAD"
[[ "$HTTP_STATUS" == '201' ]] && result=PASS || result=FAIL
report_test "$result" 33 'Driver fare estimate' 201 "$HTTP_STATUS" fare

request GET "${FARE_URL}/api/fares/final/ride/${RIDE_ID}" "$PASSENGER1_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 34 'Passenger direct final-fare retrieval' 403 "$HTTP_STATUS" fare

request GET "${FARE_URL}/api/fares/final/ride/${RIDE_ID}" "$DRIVER1_TOKEN"
[[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
report_test "$result" 35 'Driver direct final-fare retrieval' 403 "$HTTP_STATUS" fare

request GET "${FARE_URL}/api/fares/final/ride/${RIDE_ID}"
[[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
report_test "$result" 36 'Final-fare retrieval without auth' 401 "$HTTP_STATUS" fare

PAYMENT_STATUS=''
if [[ -n "$PAYMENT_ID" ]]; then
  request GET "${FARE_URL}/api/payments/${PAYMENT_ID}" "$PASSENGER1_TOKEN"
  if [[ "$HTTP_STATUS" == '200' ]]; then
    RETURNED_PASSENGER="$(json_value passengerId || true)"
    RETURNED_RIDE="$(json_value rideId || true)"
    PAYMENT_STATUS="$(json_value status || true)"
  else
    RETURNED_PASSENGER=''
    RETURNED_RIDE=''
  fi
  if [[ "$HTTP_STATUS" == '200' && "$RETURNED_PASSENGER" == "$PASSENGER1_ID" \
    && "$RETURNED_RIDE" == "$RIDE_ID" ]]; then
    report_test PASS 37 'Owner GET payment by ID' 200 "$HTTP_STATUS" payment-ownership
  else
    report_test FAIL 37 'Owner GET payment by ID' 200 "${HTTP_STATUS}-invalid-response" payment-ownership
  fi

  request GET "${FARE_URL}/api/payments/ride/${RIDE_ID}" "$PASSENGER1_TOKEN"
  RETURNED_PAYMENT="$(json_value id || true)"
  if [[ "$HTTP_STATUS" == '200' && "$RETURNED_PAYMENT" == "$PAYMENT_ID" ]]; then
    report_test PASS 38 'Owner GET payment by ride' 200 "$HTTP_STATUS" payment-ownership
  else
    report_test FAIL 38 'Owner GET payment by ride' 200 "${HTTP_STATUS}-invalid-response" payment-ownership
  fi

  request GET "${FARE_URL}/api/payments/${PAYMENT_ID}" "$PASSENGER2_TOKEN"
  [[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
  report_test "$result" 39 'Other passenger GET payment' 403 "$HTTP_STATUS" payment-ownership

  request GET "${FARE_URL}/api/payments/${PAYMENT_ID}" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
  report_test "$result" 40 'Driver GET payment' 403 "$HTTP_STATUS" payment-ownership

  request GET "${FARE_URL}/api/payments/${PAYMENT_ID}"
  [[ "$HTTP_STATUS" == '401' ]] && result=PASS || result=FAIL
  report_test "$result" 41 'Payment GET without auth' 401 "$HTTP_STATUS" payment-ownership
else
  for item in \
    '37|Owner GET payment by ID|200' \
    '38|Owner GET payment by ride|200' \
    '39|Other passenger GET payment|403' \
    '40|Driver GET payment|403' \
    '41|Payment GET without auth|401'; do
    IFS='|' read -r number name expected <<< "$item"
    report_test SKIP "$number" "$name" "$expected" missing-payment-id payment-ownership
  done
fi

CAN_REPEAT_COMPLETE=0
if [[ "$PAYMENT_STATUS" == 'PENDING' ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT_ID}/complete" "$PASSENGER1_TOKEN"
  NEW_STATUS="$(json_value status || true)"
  if [[ "$HTTP_STATUS" == '200' && "$NEW_STATUS" == 'COMPLETED' ]]; then
    report_test PASS 42 'Owner completes payment' 200 "$HTTP_STATUS" payment-lifecycle
    CAN_REPEAT_COMPLETE=1
  else
    report_test FAIL 42 'Owner completes payment' 200 "${HTTP_STATUS}-invalid-response" payment-lifecycle
  fi
elif [[ "$PAYMENT_STATUS" == 'COMPLETED' ]]; then
  report_test SKIP 42 'Owner completes payment' 200 already-completed payment-lifecycle
  CAN_REPEAT_COMPLETE=1
else
  report_test SKIP 42 'Owner completes payment' 200 no-pending-payment payment-lifecycle
fi

if [[ "$CAN_REPEAT_COMPLETE" == '1' ]]; then
  request POST "${FARE_URL}/api/payments/${PAYMENT_ID}/complete" "$PASSENGER1_TOKEN"
  [[ "$HTTP_STATUS" == '409' ]] && result=PASS || result=FAIL
  report_test "$result" 43 'Repeat payment completion' 409 "$HTTP_STATUS" payment-lifecycle
else
  report_test SKIP 43 'Repeat payment completion' 409 completion-not-performed payment-lifecycle
fi

SECOND_PAYMENT_ID=''
SECOND_PAYMENT_STATUS=''
if prepare_ride_for_payment 'Wellawatte'; then
  SECOND_RIDE_ID="$PREPARED_RIDE_ID"
  request POST "${RIDE_URL}/api/rides/${SECOND_RIDE_ID}/complete-with-payment" "$DRIVER1_TOKEN" \
    '{"distanceKm":8.25,"paymentMethod":"CASH"}'
  if [[ "$HTTP_STATUS" == '200' ]]; then
    SECOND_PAYMENT_ID="$(json_value paymentId || true)"
    if [[ -n "$SECOND_PAYMENT_ID" ]]; then
      request GET "${FARE_URL}/api/payments/${SECOND_PAYMENT_ID}" "$PASSENGER1_TOKEN"
      if [[ "$HTTP_STATUS" == '200' ]]; then
        SECOND_PAYMENT_STATUS="$(json_value status || true)"
      fi
    fi
  fi
fi

if [[ "$SECOND_PAYMENT_STATUS" == 'PENDING' ]]; then
  request POST "${FARE_URL}/api/payments/${SECOND_PAYMENT_ID}/fail" "$PASSENGER1_TOKEN"
  NEW_STATUS="$(json_value status || true)"
  if [[ "$HTTP_STATUS" == '200' && "$NEW_STATUS" == 'FAILED' ]]; then
    report_test PASS 44 'Owner fails payment' 200 "$HTTP_STATUS" payment-lifecycle
    request POST "${FARE_URL}/api/payments/${SECOND_PAYMENT_ID}/fail" "$PASSENGER1_TOKEN"
    [[ "$HTTP_STATUS" == '409' ]] && result=PASS || result=FAIL
    report_test "$result" 45 'Repeat payment failure' 409 "$HTTP_STATUS" payment-lifecycle
  else
    report_test FAIL 44 'Owner fails payment' 200 "${HTTP_STATUS}-invalid-response" payment-lifecycle
    report_test SKIP 45 'Repeat payment failure' 409 failure-not-performed payment-lifecycle
  fi
else
  report_test SKIP 44 'Owner fails payment' 200 no-pending-payment payment-lifecycle
  report_test SKIP 45 'Repeat payment failure' 409 no-failed-payment payment-lifecycle
fi

STATUS_PAYMENT_ID="$SECOND_PAYMENT_ID"
[[ -n "$STATUS_PAYMENT_ID" ]] || STATUS_PAYMENT_ID="$PAYMENT_ID"
if [[ -n "$STATUS_PAYMENT_ID" ]]; then
  request POST "${FARE_URL}/api/payments/${STATUS_PAYMENT_ID}/complete" "$PASSENGER2_TOKEN"
  [[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
  report_test "$result" 46 'Unrelated passenger changes payment status' 403 "$HTTP_STATUS" \
    payment-lifecycle

  request POST "${FARE_URL}/api/payments/${STATUS_PAYMENT_ID}/complete" "$DRIVER1_TOKEN"
  [[ "$HTTP_STATUS" == '403' ]] && result=PASS || result=FAIL
  report_test "$result" 47 'Driver changes payment status' 403 "$HTTP_STATUS" payment-lifecycle
else
  report_test SKIP 46 'Unrelated passenger changes payment status' 403 missing-payment-id \
    payment-lifecycle
  report_test SKIP 47 'Driver changes payment status' 403 missing-payment-id payment-lifecycle
fi

request GET "${DRIVER_URL}/api/drivers/${DRIVER1_PROFILE_ID}" 'obviously-malformed-token'
if check_json_unauthorized; then
  report_test PASS 48 'Malformed JWT on Driver endpoint' 401-json 401-json driver
else
  report_test FAIL 48 'Malformed JWT on Driver endpoint' 401-json "$HTTP_STATUS" driver
fi

request GET "${RIDE_URL}/api/rides/${RIDE_ID}" 'obviously-malformed-token'
if check_json_unauthorized; then
  report_test PASS 49 'Malformed JWT on Ride endpoint' 401-json 401-json ride-passenger
else
  report_test FAIL 49 'Malformed JWT on Ride endpoint' 401-json "$HTTP_STATUS" ride-passenger
fi

request GET "${FARE_URL}/api/fares/estimates/not-a-real-estimate" 'obviously-malformed-token'
if check_json_unauthorized; then
  report_test PASS 50 'Malformed JWT on Fare endpoint' 401-json 401-json fare
else
  report_test FAIL 50 'Malformed JWT on Fare endpoint' 401-json "$HTTP_STATUS" fare
fi

for swagger_test in \
  "51|Account|${ACCOUNT_URL}" \
  "52|Driver|${DRIVER_URL}" \
  "53|Ride|${RIDE_URL}" \
  "54|Fare/Payment|${FARE_URL}"; do
  IFS='|' read -r number service_name service_url <<< "$swagger_test"
  request GET "${service_url}/v3/api-docs"
  [[ "$HTTP_STATUS" == '200' ]] && result=PASS || result=FAIL
  report_test "$result" "$number" "${service_name} API docs public" 200 "$HTTP_STATUS" swagger
done

for swagger_ui in \
  "Account|${ACCOUNT_URL}" \
  "Driver|${DRIVER_URL}" \
  "Ride|${RIDE_URL}" \
  "Fare/Payment|${FARE_URL}"; do
  IFS='|' read -r service_name service_url <<< "$swagger_ui"
  request GET "${service_url}/swagger-ui/index.html"
  if [[ "$HTTP_STATUS" == '200' ]]; then
    report_verification PASS "${service_name} Swagger UI" 200 "$HTTP_STATUS" swagger
  else
    report_verification FAIL "${service_name} Swagger UI" 200 "$HTTP_STATUS" swagger
  fi
done

printf '%s\n' \
  "$PASSENGER1_TOKEN" "$PASSENGER2_TOKEN" "$DRIVER1_TOKEN" "$DRIVER2_TOKEN" \
  "$TEST_PASSWORD" "$INVALID_PASSWORD" > "$SENSITIVE_PATTERNS"

LOG_REVIEW_RESULT='PASS'
for log_file in \
  '.logs/account.log' \
  '.logs/driver.log' \
  '.logs/ride.log' \
  '.logs/fare-payment.log'; do
  if [[ ! -f "$log_file" ]]; then
    echo "Security log review issue: expected log file is missing: ${log_file}." >&2
    LOG_REVIEW_RESULT='FAIL'
    continue
  fi
  if grep -Fq -f "$SENSITIVE_PATTERNS" "$log_file" \
    || grep -Eqi 'mongodb(\+srv)?://[^[:space:]]+' "$log_file" \
    || grep -Eqi '(JWT_SECRET|INTERNAL_SERVICE_KEY)[[:space:]]*[:=][[:space:]]*[^[:space:]]+' "$log_file"; then
    echo "Security log review issue: credential-like content detected in ${log_file}." >&2
    LOG_REVIEW_RESULT='FAIL'
  fi
done

echo
echo 'RIDELINK FINAL SECURED SYSTEM SMOKE TEST'
echo
echo "Account authentication: ${ACCOUNT_RESULT}"
echo "Driver authorization: ${DRIVER_RESULT}"
echo "Vehicle authorization: ${VEHICLE_RESULT}"
echo "Ride passenger ownership: ${RIDE_PASSENGER_RESULT}"
echo "Assigned-driver ownership: ${ASSIGNED_DRIVER_RESULT}"
echo "Ride -> Driver internal auth: ${RIDE_DRIVER_INTERNAL_RESULT}"
echo "Ride -> Fare/Payment internal auth: ${RIDE_FARE_INTERNAL_RESULT}"
echo "Fare authorization: ${FARE_RESULT}"
echo "Payment ownership: ${PAYMENT_OWNERSHIP_RESULT}"
echo "Payment lifecycle: ${PAYMENT_LIFECYCLE_RESULT}"
echo "Driver availability synchronization: ${DRIVER_AVAILABILITY_RESULT}"
echo "Swagger: ${SWAGGER_RESULT}"
echo "Security log review: ${LOG_REVIEW_RESULT}"
echo
echo "Passed: ${PASSED}"
echo "Failed: ${FAILED}"
echo "Skipped: ${SKIPPED}"

if [[ "$FAILED" -gt 0 \
  || "$DRIVER_AVAILABILITY_RESULT" == 'FAIL' \
  || "$SWAGGER_RESULT" == 'FAIL' \
  || "$LOG_REVIEW_RESULT" == 'FAIL' ]]; then
  exit 1
fi
