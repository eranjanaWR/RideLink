package com.ridelink.farepayment.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SwaggerProbeController {

    @GetMapping("/v3/api-docs/security-test")
    String apiDocs() {
        return "api-docs";
    }

    @GetMapping("/swagger-ui/security-test")
    String swaggerUi() {
        return "swagger-ui";
    }
}
