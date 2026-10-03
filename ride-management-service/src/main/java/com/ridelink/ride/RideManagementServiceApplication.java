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
                description = "Creates, retrieves, assigns, and manages the lifecycle of ride requests. Integrated "
                        + "completion can obtain a final fare and create a simulated payment through Fare & Payment "
                        + "Service. Driver availability is synchronized through Driver & Vehicle Service."
        )
)
public class RideManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideManagementServiceApplication.class, args);
    }
}
