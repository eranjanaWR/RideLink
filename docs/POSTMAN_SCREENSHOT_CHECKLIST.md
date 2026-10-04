# RideLink Postman screenshot checklist

Capture responses with the **RideLink Local** environment selected. Collapse or crop Postman's environment panel and request authorization values before every screenshot.

| ID | Exact request or page | Expected | Must be visible | Must be hidden or cropped |
|---|---|---|---|---|
| PS-01 | `01 - Account / Register PASSENGER` | `201 Created` | Request name, status, returned account ID, role `PASSENGER`, status `ACTIVE` | Password field, environment values |
| PS-02 | `01 - Account / Login PASSENGER` | `200 OK` | Request name, status, `tokenType: Bearer`, account role | `accessToken` value, password |
| PS-03 | `06 - Security / Error Cases / 401 - Missing JWT` | `401 Unauthorized` | JSON error status/message and request name | Any tokens or environment panel |
| PS-04 | `02 - Driver & Vehicle / Create driver profile` | `201 Created` | Driver profile ID, account ID, licence number, service area | Authorization token |
| PS-05 | `02 - Driver & Vehicle / Create vehicle` | `201 Created` | Vehicle ID, registration number, `vehicleType: CAR` | Authorization token |
| PS-06 | `02 - Driver & Vehicle / Eligible driver search` | `200 OK` | Driver, vehicle, `AVAILABLE`, service area, location | Authorization token |
| PS-07 | `03 - Ride Management / Create ride` | `201 Created` | Ride ID, passenger ID, service area, `REQUESTED` | Authorization token |
| PS-08 | `03 - Ride Management / Assign driver` | `200 OK` | Ride ID, assigned driver ID, `ASSIGNED` | Authorization token, internal credentials |
| PS-09 | `03 - Ride Management / Accept ride`, then `Start ride` | `200 OK` | Request name and resulting `ACCEPTED` or `IN_PROGRESS` status | Driver JWT |
| PS-10 | `03 - Ride Management / Complete with payment` | `200 OK` | `COMPLETED`, positive `finalFare`, `paymentId`, `completedAt` | Driver JWT, internal credentials |
| PS-11 | `05 - Payment / Get payment by ID` | `200 OK` | Payment ID, ride ID, passenger ID, amount, currency, status | Passenger JWT |
| PS-12 | `06 - Security / Error Cases / 403 - Wrong role` or ownership request | `403 Forbidden` | Request name and JSON 403 error | JWT values |
| PS-13 | `06 - Security / Error Cases / 409 - Invalid ride lifecycle transition` | `409 Conflict` | Request name and JSON conflict message | JWT values |
| PS-14 | Browser: `http://localhost:8081/swagger-ui/index.html` | Swagger UI loads | Account Service operations/title | Browser history containing credentials |
| PS-15 | Browser: `http://localhost:8082/swagger-ui/index.html` | Swagger UI loads | Driver and Vehicle operations/title | Authorization dialog contents |
| PS-16 | Browser: `http://localhost:8083/swagger-ui/index.html` | Swagger UI loads | Ride operations/title | Authorization dialog contents |
| PS-17 | Browser: `http://localhost:8084/swagger-ui/index.html` | Swagger UI loads | Fare and Payment operations/title | Authorization dialog contents |

## Never show

- JWT token values
- MongoDB URIs or database credentials
- Account passwords
- `JWT_SECRET`
- `INTERNAL_SERVICE_KEY`
- Populated Postman environment exports
