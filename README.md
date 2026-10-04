# RideLink Backend

RideLink is a backend-only ride-sharing platform built as four independently executable Spring Boot microservices. It demonstrates account authentication, driver and vehicle management, ride assignment and lifecycle processing, deterministic fare calculation, simulated payments, service-owned MongoDB persistence, and secured synchronous REST integration.

## Architecture overview

Clients use REST/JSON through Postman or the Swagger UIs. Account Service registers users and issues JWT Bearer tokens. Driver & Vehicle, Ride Management, and Fare & Payment independently validate those JWTs; they do not call Account Service or read its database during authentication.

Ride Management coordinates the business workflow. It uses `X-Internal-Service-Key` authentication for trusted calls to Driver & Vehicle and Fare & Payment. Every service owns a separate MongoDB database, and no service directly accesses another service's data store.

- [System architecture diagram](docs/architecture-diagram.md)
- [Git branch workflow diagram](docs/git-branch-diagram.md)
- [Optional diagram-generation prompts](docs/diagram-generation-prompts.md)

## Microservices

| Service | Responsibilities | Owner | Port | Database |
|---|---|---|---:|---|
| Account Service | Registration, login, profile management, account status, JWT issuance | Dineth | 8081 | `ridelink_account_db` |
| Driver & Vehicle Service | Driver profiles, vehicle CRUD, simulated location, availability, eligible-driver search | Eranjana | 8082 | `ridelink_driver_db` |
| Ride Management Service | Ride requests, assignment, lifecycle, cancellation, integrated completion with payment | Eranjana | 8083 | `ridelink_ride_db` |
| Fare & Payment Service | Fare estimates, final fares, payment records, payment transitions | Dineth | 8084 | `ridelink_fare_payment_db` |

## Technology stack

- Java 21 and Spring Boot 3.x
- Maven
- Spring Web, Spring Validation, and Spring Data MongoDB
- Spring Security and JJWT
- MongoDB Atlas
- REST/JSON
- Swagger/OpenAPI and Postman
- JUnit 5, Mockito, and Spring MockMvc
- GitHub Actions CI

## Functional features

- PASSENGER and DRIVER registration and login
- Account profile retrieval and updates; ADMIN-controlled account status
- Driver profile, location, availability, and vehicle management
- Eligible-driver discovery by service area and operational readiness
- Passenger-owned ride creation, retrieval, assignment, and cancellation
- Assigned-driver acceptance, start, completion, and completion with payment
- Automatic `AVAILABLE` → `UNAVAILABLE` → `AVAILABLE` driver synchronization
- Deterministic estimates and final fares using configurable rates and minimum fare
- One final fare and one simulated payment per ride with conflict-safe reuse
- Payment retrieval and `PENDING` → `COMPLETED` or `PENDING` → `FAILED` transitions

## Security design

Account Service signs JWT Bearer tokens using a shared signing secret. Tokens contain the account ID as the subject plus `email`, `role`, and `status` claims.

Supported roles are `PASSENGER`, `DRIVER`, and `ADMIN`; supported statuses are `ACTIVE`, `SUSPENDED`, and `DISABLED`. Only `ACTIVE` accounts authenticate. Protected services independently verify the signature, expiration, subject, role, and status. Authorization then enforces role, account ownership, driver ownership, ride ownership, assigned-driver ownership, and payment ownership as appropriate.

The APIs are stateless, CSRF is disabled for REST usage, and form login and HTTP Basic are disabled. Swagger and OpenAPI endpoints remain public. Authentication and authorization failures use JSON responses without exposing parsing or credential details.

## Interservice communication

All integration is synchronous REST:

- Ride → Driver & Vehicle: eligible-driver search and availability synchronization
- Ride → Fare & Payment: final-fare creation/reuse and payment creation/reuse

These trusted backend calls use the shared `X-Internal-Service-Key`. User JWTs are not forwarded as a replacement for service authentication. Normal user-facing requests use `Authorization: Bearer <token>`.

## Database ownership

Each service may access only its own MongoDB database:

- Account Service → `ridelink_account_db`
- Driver & Vehicle Service → `ridelink_driver_db`
- Ride Management Service → `ridelink_ride_db`
- Fare & Payment Service → `ridelink_fare_payment_db`

Cross-service database queries and shared collections are intentionally prohibited; integration occurs only through REST contracts.

## Port configuration

| Service | Default URL |
|---|---|
| Account | `http://localhost:8081` |
| Driver & Vehicle | `http://localhost:8082` |
| Ride Management | `http://localhost:8083` |
| Fare & Payment | `http://localhost:8084` |

## Environment variables

Use placeholders in committed files and keep real local values in `.env.local`.

| Variable | Purpose |
|---|---|
| `ACCOUNT_MONGODB_URI` | Account Service MongoDB connection URI |
| `DRIVER_MONGODB_URI` | Driver & Vehicle Service MongoDB connection URI |
| `RIDE_MONGODB_URI` | Ride Management Service MongoDB connection URI |
| `FARE_PAYMENT_MONGODB_URI` | Fare & Payment Service MongoDB connection URI |
| `JWT_SECRET` | Shared HMAC secret used to issue and validate JWTs |
| `INTERNAL_SERVICE_KEY` | Shared key for trusted backend-to-backend calls |
| `ACCOUNT_PORT` | Optional Account port override |
| `DRIVER_PORT` | Optional Driver & Vehicle port override |
| `RIDE_PORT` | Optional Ride Management port override |
| `PAYMENT_PORT` | Optional Fare & Payment port override |

`.env.local` is recommended for local development and must never be committed. Do not place real credentials in source code, `application.yml`, Postman exports, screenshots, or reports.

## Local setup

Prerequisites:

- Java 21
- Maven 3.9 or newer
- Bash on macOS/Linux, or PowerShell on Windows
- Four MongoDB Atlas connection URIs with network access configured

Setup:

1. Clone the repository.
2. Copy `.env.example` to `.env.local`.
3. Fill the required local MongoDB URIs, `JWT_SECRET`, and `INTERNAL_SERVICE_KEY` in `.env.local`.
4. Keep `.env.local` private and untracked.

## Running all services

From the repository root on macOS/Linux:

```bash
./run-local.sh
```

On Windows PowerShell:

```powershell
.\run-local.ps1
```

The launchers validate configuration, start all four services, and write sanitized service output to:

- `.logs/account.log`
- `.logs/driver.log`
- `.logs/ride.log`
- `.logs/fare-payment.log`

## Swagger/OpenAPI

| Service | Swagger UI |
|---|---|
| Account | <http://localhost:8081/swagger-ui/index.html> |
| Driver & Vehicle | <http://localhost:8082/swagger-ui/index.html> |
| Ride Management | <http://localhost:8083/swagger-ui/index.html> |
| Fare & Payment | <http://localhost:8084/swagger-ui/index.html> |

Each service also exposes public OpenAPI JSON at `/v3/api-docs`.

## Postman

Import and select:

- [`postman/RideLink.postman_collection.json`](postman/RideLink.postman_collection.json)
- [`postman/RideLink.postman_environment.json`](postman/RideLink.postman_environment.json)

Follow [`postman/README.md`](postman/README.md) for setup. The collection captures JWTs and generated IDs without logging them. Use **07 - End-to-End Demo** for the ordered presentation workflow. The final-report screenshot plan is in [`docs/POSTMAN_SCREENSHOT_CHECKLIST.md`](docs/POSTMAN_SCREENSHOT_CHECKLIST.md).

## Testing

Run a service's tests from its directory:

```bash
mvn clean test
```

Verified final automated-test results:

| Service | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Account | 177 | 0 | 0 | 0 |
| Driver & Vehicle | 120 | 0 | 0 | 0 |
| Ride Management | 288 | 0 | 0 | 0 |
| Fare & Payment | 254 | 0 | 0 | 0 |
| **Total** | **839** | **0** | **0** | **0** |

The final secured-system live smoke test also passed **54/54 checks**, with **0 failed** and **0 skipped**. It covered authentication, ownership, role authorization, both trusted integration paths, ride completion with payment, driver availability synchronization, malformed authentication, Swagger access, and sanitized log review.

## CI/CD

GitHub Actions workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs a Java 21 matrix build across all four services and executes `mvn clean test` independently for each one.

Triggers:

- `push` to `dev`, `release`, or `main`
- `pull_request` targeting `dev`, `release`, or `main`
- manual `workflow_dispatch`

The repository currently implements continuous integration only; deployment remains outside the assignment scope.

## Git workflow

Development follows this flow:

```text
feature/... | chore/... | fix/...
               ↓
              dev
               ↓
            release
               ↓
             main
               ↓
           tag v1.0.0
```

Examples include `feature/eranjana/...` and `feature/dineth/...`. Changes reach shared branches through pull requests and peer review: Eranjana's changes are reviewed by Dineth, and Dineth's changes are reviewed by Eranjana. See the [Git branch diagram](docs/git-branch-diagram.md).

## Team responsibilities

| Team member | Responsibilities |
|---|---|
| Eranjana | Driver & Vehicle Service, Ride Management Service, integration/security coordination, CI and documentation support |
| Dineth | Account Service, Fare & Payment Service |

## Final validation status

- Four independently executable services: complete
- JWT authentication and role/ownership authorization: verified
- Ride → Driver internal authentication: verified
- Ride → Fare & Payment internal authentication: verified
- Service-owned MongoDB persistence boundaries: verified
- Automated tests: 839 passed
- Final secured live smoke test: 54 passed
- Swagger/OpenAPI and final Postman collection: prepared
- GitHub Actions Java 21 matrix CI: configured

## Repository structure

```text
RideLink/
├── account-service/                  # Account authentication and profiles
├── driver-and-vehicle-service/       # Driver operations and vehicle CRUD
├── ride-management-service/          # Ride orchestration and lifecycle
├── fare-and-payment-service/         # Fare and simulated payment records
├── docs/                             # Diagrams, report, and screenshot guidance
├── postman/                          # Final collection, environment, and guide
├── .github/workflows/ci.yml          # Four-service Java 21 CI matrix
├── .env.example                      # Safe configuration template
├── run-local.sh                      # macOS/Linux launcher
└── run-local.ps1                     # Windows PowerShell launcher
```

## Known limitations and assignment scope

- Backend only; no frontend application is included.
- Driver location is simulated; there is no live GPS or maps integration.
- Eligible drivers are filtered by service area and readiness without geographic distance ranking.
- CASH and CARD payments are simulated; no external payment gateway is contacted.
- Service integration is synchronous REST; no message broker such as Kafka is used.
- No API gateway or service discovery layer is included.
- Cross-service workflows do not provide distributed transactions.
- The shared internal service key is assignment-level service authentication, not production-grade workload identity or mutual TLS.

## AI usage declaration

> Submission placeholder: describe any AI tools used for planning, implementation assistance, testing, or documentation, together with the team's review and validation process, according to institutional requirements.
