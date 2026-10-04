#!/usr/bin/env bash

# One-time setup (macOS/Linux):
#   cp .env.example .env.local
#   # Fill in the private values. Quote values containing shell-special characters.
#   chmod +x run-local.sh
#   ./run-local.sh

set -Eeuo pipefail

readonly RIDELINK_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly RIDELINK_PREFERRED_ENV_FILE="${RIDELINK_ROOT}/.env.local"
readonly RIDELINK_LEGACY_ENV_FILE="${RIDELINK_ROOT}/.env"
readonly RIDELINK_LOG_DIR="${RIDELINK_ROOT}/.logs"

readonly -a RIDELINK_REQUIRED_VARIABLES=(
  ACCOUNT_MONGODB_URI
  DRIVER_MONGODB_URI
  RIDE_MONGODB_URI
  FARE_PAYMENT_MONGODB_URI
  JWT_SECRET
  INTERNAL_SERVICE_KEY
)

if [[ -f "${RIDELINK_PREFERRED_ENV_FILE}" ]]; then
  RIDELINK_ENV_FILE="${RIDELINK_PREFERRED_ENV_FILE}"
  RIDELINK_ENV_FILE_NAME='.env.local'
elif [[ -f "${RIDELINK_LEGACY_ENV_FILE}" ]]; then
  RIDELINK_ENV_FILE="${RIDELINK_LEGACY_ENV_FILE}"
  RIDELINK_ENV_FILE_NAME='.env'
  printf '.env.local not found; using legacy .env\n'
else
  printf 'No local environment file found.\n' >&2
  printf 'Create .env.local from .env.example.\n' >&2
  exit 1
fi

# Ensure validation checks values loaded from the selected file, not inherited values.
unset ACCOUNT_MONGODB_URI DRIVER_MONGODB_URI RIDE_MONGODB_URI
unset FARE_PAYMENT_MONGODB_URI PAYMENT_MONGODB_URI JWT_SECRET INTERNAL_SERVICE_KEY
unset ACCOUNT_PORT DRIVER_PORT RIDE_PORT PAYMENT_PORT

# The selected file is developer-controlled and uses shell-compatible KEY=value entries.
# shellcheck disable=SC1090
source "${RIDELINK_ENV_FILE}"

if [[ -z "${FARE_PAYMENT_MONGODB_URI:-}" && -n "${PAYMENT_MONGODB_URI:-}" ]]; then
  FARE_PAYMENT_MONGODB_URI="${PAYMENT_MONGODB_URI}"
  printf 'Using legacy PAYMENT_MONGODB_URI; prefer FARE_PAYMENT_MONGODB_URI.\n'
fi

ACCOUNT_PORT="${ACCOUNT_PORT:-8081}"
DRIVER_PORT="${DRIVER_PORT:-8082}"
RIDE_PORT="${RIDE_PORT:-8083}"
PAYMENT_PORT="${PAYMENT_PORT:-8084}"

for RIDELINK_PORT_NAME in ACCOUNT_PORT DRIVER_PORT RIDE_PORT PAYMENT_PORT; do
  RIDELINK_PORT_VALUE="${!RIDELINK_PORT_NAME}"
  if [[ ! "${RIDELINK_PORT_VALUE}" =~ ^[0-9]{1,5}$ ]] ||
    (( 10#${RIDELINK_PORT_VALUE} < 1 || 10#${RIDELINK_PORT_VALUE} > 65535 )); then
    printf 'Error: %s must be an integer from 1 to 65535.\n' \
      "${RIDELINK_PORT_NAME}" >&2
    exit 1
  fi
done

RIDELINK_MISSING_VARIABLES=()
for RIDELINK_VARIABLE_NAME in "${RIDELINK_REQUIRED_VARIABLES[@]}"; do
  if [[ -z "${!RIDELINK_VARIABLE_NAME:-}" ]]; then
    RIDELINK_MISSING_VARIABLES+=("${RIDELINK_VARIABLE_NAME}")
  fi
done

if (( ${#RIDELINK_MISSING_VARIABLES[@]} > 0 )); then
  for RIDELINK_VARIABLE_NAME in "${RIDELINK_MISSING_VARIABLES[@]}"; do
    printf 'Error: Missing %s in %s.\n' \
      "${RIDELINK_VARIABLE_NAME}" "${RIDELINK_ENV_FILE_NAME}" >&2
  done
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  printf 'Error: Maven (mvn) was not found on PATH.\n' >&2
  exit 1
fi

for RIDELINK_SERVICE_DIRECTORY in \
  account-service \
  driver-and-vehicle-service \
  fare-and-payment-service \
  ride-management-service; do
  if [[ ! -f "${RIDELINK_ROOT}/${RIDELINK_SERVICE_DIRECTORY}/pom.xml" ]]; then
    printf 'Error: expected service not found: %s\n' "${RIDELINK_SERVICE_DIRECTORY}" >&2
    exit 1
  fi
done

mkdir -p "${RIDELINK_LOG_DIR}"

RIDELINK_PIDS=()
RIDELINK_SERVICE_NAMES=()
RIDELINK_CLEANING_UP=0

signal_process_tree() {
  local parent_pid="$1"
  local signal_name="$2"
  local child_pid
  local child_pids=""

  if command -v pgrep >/dev/null 2>&1; then
    child_pids="$(pgrep -P "${parent_pid}" 2>/dev/null || true)"
  fi

  kill "-${signal_name}" "${parent_pid}" 2>/dev/null || true

  for child_pid in ${child_pids}; do
    signal_process_tree "${child_pid}" "${signal_name}"
  done
}

cleanup() {
  local exit_status=$?
  local pid
  local attempt
  local any_running

  trap - EXIT INT TERM

  if (( RIDELINK_CLEANING_UP == 1 )); then
    exit "${exit_status}"
  fi
  RIDELINK_CLEANING_UP=1

  if (( ${#RIDELINK_PIDS[@]} > 0 )); then
    printf '\nStopping RideLink services...\n'

    for pid in "${RIDELINK_PIDS[@]}"; do
      if kill -0 "${pid}" 2>/dev/null; then
        signal_process_tree "${pid}" TERM
      fi
    done

    for attempt in {1..50}; do
      any_running=0
      for pid in "${RIDELINK_PIDS[@]}"; do
        if kill -0 "${pid}" 2>/dev/null; then
          any_running=1
          break
        fi
      done
      (( any_running == 0 )) && break
      sleep 0.1
    done

    for pid in "${RIDELINK_PIDS[@]}"; do
      if kill -0 "${pid}" 2>/dev/null; then
        signal_process_tree "${pid}" KILL
      fi
      wait "${pid}" 2>/dev/null || true
    done

    printf 'All RideLink services stopped.\n'
  fi

  exit "${exit_status}"
}

start_service() {
  local service_name="$1"
  local service_directory="$2"
  local log_file="$3"
  shift 3

  : > "${log_file}"

  (
    cd "${RIDELINK_ROOT}/${service_directory}"
    exec env \
      -u ACCOUNT_MONGODB_URI \
      -u DRIVER_MONGODB_URI \
      -u RIDE_MONGODB_URI \
      -u FARE_PAYMENT_MONGODB_URI \
      -u PAYMENT_MONGODB_URI \
      -u JWT_SECRET \
      -u INTERNAL_SERVICE_KEY \
      -u ACCOUNT_PORT \
      -u DRIVER_PORT \
      -u RIDE_PORT \
      -u PAYMENT_PORT \
      -u MONGODB_URI \
      -u SERVER_PORT \
      -u DRIVER_SERVICE_URL \
      -u FARE_PAYMENT_SERVICE_URL \
      "$@" mvn spring-boot:run
  ) >> "${log_file}" 2>&1 &

  RIDELINK_PIDS+=("$!")
  RIDELINK_SERVICE_NAMES+=("${service_name}")
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

start_service \
  'Account Service' \
  'account-service' \
  "${RIDELINK_LOG_DIR}/account.log" \
  "MONGODB_URI=${ACCOUNT_MONGODB_URI}" \
  "JWT_SECRET=${JWT_SECRET}" \
  "SERVER_PORT=${ACCOUNT_PORT}"

start_service \
  'Driver & Vehicle Service' \
  'driver-and-vehicle-service' \
  "${RIDELINK_LOG_DIR}/driver.log" \
  "MONGODB_URI=${DRIVER_MONGODB_URI}" \
  "JWT_SECRET=${JWT_SECRET}" \
  "INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY}" \
  "SERVER_PORT=${DRIVER_PORT}"

start_service \
  'Fare & Payment Service' \
  'fare-and-payment-service' \
  "${RIDELINK_LOG_DIR}/fare-payment.log" \
  "MONGODB_URI=${FARE_PAYMENT_MONGODB_URI}" \
  "JWT_SECRET=${JWT_SECRET}" \
  "INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY}" \
  "SERVER_PORT=${PAYMENT_PORT}"

start_service \
  'Ride Management Service' \
  'ride-management-service' \
  "${RIDELINK_LOG_DIR}/ride.log" \
  "MONGODB_URI=${RIDE_MONGODB_URI}" \
  "JWT_SECRET=${JWT_SECRET}" \
  "INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY}" \
  "SERVER_PORT=${RIDE_PORT}" \
  "DRIVER_SERVICE_URL=http://localhost:${DRIVER_PORT}" \
  "FARE_PAYMENT_SERVICE_URL=http://localhost:${PAYMENT_PORT}"

printf '%s\n' \
  'RideLink local backend starting...' \
  '' \
  "Account Service           http://localhost:${ACCOUNT_PORT}" \
  "Driver & Vehicle Service  http://localhost:${DRIVER_PORT}" \
  "Ride Management Service   http://localhost:${RIDE_PORT}" \
  "Fare & Payment Service    http://localhost:${PAYMENT_PORT}" \
  '' \
  "Using environment file: ${RIDELINK_ENV_FILE_NAME}" \
  '' \
  'Logs:' \
  '.logs/account.log' \
  '.logs/driver.log' \
  '.logs/ride.log' \
  '.logs/fare-payment.log' \
  '' \
  'Press Ctrl+C to stop all RideLink services.'

while true; do
  for RIDELINK_INDEX in "${!RIDELINK_PIDS[@]}"; do
    RIDELINK_PID="${RIDELINK_PIDS[RIDELINK_INDEX]}"
    if ! kill -0 "${RIDELINK_PID}" 2>/dev/null; then
      set +e
      wait "${RIDELINK_PID}"
      RIDELINK_SERVICE_STATUS=$?
      set -e

      printf 'Error: %s stopped (exit code %s). Check its log for details.\n' \
        "${RIDELINK_SERVICE_NAMES[RIDELINK_INDEX]}" \
        "${RIDELINK_SERVICE_STATUS}" >&2

      if (( RIDELINK_SERVICE_STATUS == 0 )); then
        exit 1
      fi
      exit "${RIDELINK_SERVICE_STATUS}"
    fi
  done
  sleep 1
done
