package com.ridelink.ride;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "RideLink Ride Management API",
                version = "v1",
                description = "Creates and retrieves ride requests and assigns eligible drivers through the Driver & "
                        + "Vehicle Service. Fare calculation and driver availability updates are not part of this feature."
        )
)
public class RideManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideManagementServiceApplication.class, args);
    }
}
