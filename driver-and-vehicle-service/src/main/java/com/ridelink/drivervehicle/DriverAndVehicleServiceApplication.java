package com.ridelink.drivervehicle;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@OpenAPIDefinition(info = @Info(
        title = "RideLink Driver & Vehicle Service API",
        version = "v1",
        description = "User operations require an ACTIVE Account Service JWT and enforce role and ownership rules."))
@SecuritySchemes({
        @SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT"),
        @SecurityScheme(
                name = "internalServiceKey",
                type = SecuritySchemeType.APIKEY,
                in = SecuritySchemeIn.HEADER,
                paramName = "X-Internal-Service-Key",
                description = "Trusted Ride Management Service authentication for eligible-driver and availability operations")
})
public class DriverAndVehicleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverAndVehicleServiceApplication.class, args);
    }
}
