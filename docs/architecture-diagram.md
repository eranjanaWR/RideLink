# RideLink system architecture

```mermaid
flowchart LR
    Client["Client<br/>Postman / Swagger"]
    Users["PASSENGER / DRIVER / ADMIN"]

    subgraph Services["RideLink Spring Boot microservices"]
        Account["Account Service<br/>:8081<br/>Registers users and issues JWTs"]
        Driver["Driver & Vehicle Service<br/>:8082<br/>Validates JWT independently"]
        Ride["Ride Management Service<br/>:8083<br/>Validates JWT independently"]
        Fare["Fare & Payment Service<br/>:8084<br/>Validates JWT independently"]
    end

    subgraph Databases["Service-owned MongoDB databases"]
        AccountDB[("ridelink_account_db")]
        DriverDB[("ridelink_driver_db")]
        RideDB[("ridelink_ride_db")]
        FareDB[("ridelink_fare_payment_db")]
    end

    Users -->|"Register / login"| Client
    Client -->|"Public REST"| Account
    Account -.->|"Issues signed JWT"| Client
    Client -->|"REST + Bearer JWT"| Driver
    Client -->|"REST + Bearer JWT"| Ride
    Client -->|"REST + Bearer JWT"| Fare

    Ride -->|"REST: eligible search and availability sync<br/>X-Internal-Service-Key"| Driver
    Ride -->|"REST: final fare and payment create/reuse<br/>X-Internal-Service-Key"| Fare

    Account -->|"Own database only"| AccountDB
    Driver -->|"Own database only"| DriverDB
    Ride -->|"Own database only"| RideDB
    Fare -->|"Own database only"| FareDB

    Isolation["No cross-service database access<br/>No shared database"]

    classDef client fill:#e8f1ff,stroke:#2563eb,color:#172554
    classDef service fill:#ecfdf5,stroke:#059669,color:#064e3b
    classDef database fill:#fff7ed,stroke:#ea580c,color:#7c2d12
    classDef rule fill:#fef2f2,stroke:#dc2626,color:#7f1d1d
    class Client,Users client
    class Account,Driver,Ride,Fare service
    class AccountDB,DriverDB,RideDB,FareDB database
    class Isolation rule
```

## Trust boundaries

- User-facing protected REST requests carry an Account Service JWT as a Bearer token.
- Driver & Vehicle, Ride Management, and Fare & Payment validate JWTs independently.
- Ride authenticates its trusted downstream calls with `X-Internal-Service-Key`.
- Database arrows are deliberately one-to-one: a service never reads another service's MongoDB database.
