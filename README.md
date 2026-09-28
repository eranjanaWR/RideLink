# RideLink

RideLink is a backend-only ride-sharing platform implemented as four independently executable Spring Boot microservices.

## Team

| Name | Role | Service Ownership |
| --- | --- | --- |
| Eranjana | Team Lead | Driver & Vehicle Service, Ride Management Service |
| Dineth | Member | Account Service, Fare & Payment Service |

The two-member group and two-services-per-member ownership structure has lecturer approval.

## Technology Stack

- Java 21
- Spring Boot 3.x
- Maven
- MongoDB
- Spring Data MongoDB
- Spring Web
- Spring Security + JWT planned
- Jakarta Bean Validation
- Swagger/OpenAPI
- Postman
- JUnit 5
- Mockito
- GitHub Actions planned

## Microservices

| Service | Owner | Port | Database |
| --- | --- | ---: | --- |
| Account Service | Dineth | 8081 | `ridelink_account_db` |
| Driver & Vehicle Service | Eranjana | 8082 | `ridelink_driver_db` |
| Ride Management Service | Eranjana | 8083 | `ridelink_ride_db` |
| Fare & Payment Service | Dineth | 8084 | `ridelink_fare_payment_db` |

## Architecture Rules

- The system consists of four independently executable services.
- Each service owns its own MongoDB persistence boundary.
- Services must not directly access another service's database.
- Cross-service communication will use defined service APIs.
- REST/JSON will be the initial communication approach.
- Swagger/OpenAPI and Postman are the official interfaces for exercising the APIs.
- No frontend is required.

## Git Workflow

The team uses the following lightweight GitFlow:

```text
feature/* -> Pull Request -> develop
develop -> release/v1.0.0
release/v1.0.0 -> Pull Request -> main
main -> tag v1.0.0
```

- `main` contains stable, release-ready work.
- `develop` contains integrated development work.
- Feature branches are created from `develop`.
- Feature branches are short-lived.
- Eranjana's branches use `feature/eranjana/<feature-name>`.
- Dineth's branches use `feature/dineth/<feature-name>`.
- Pull requests require peer review before merging into `develop`.
- Release branches are created only when preparing a release.
- Future feature branches are not created during this bootstrap step.

## Repository Structure

- `account-service/` — Account Service workspace.
- `driver-and-vehicle-service/` — Driver & Vehicle Service workspace.
- `ride-management-service/` — Ride Management Service workspace.
- `fare-and-payment-service/` — Fare & Payment Service workspace.
- `docs/` — Architecture, design, testing, and report documentation.
- `postman/` — Postman collections, environments, and API workflow examples.
- `.github/workflows/` — GitHub Actions workflows to be added later.

## Current Status

This repository is in its initial bootstrap stage. No business functionality has been implemented yet.
