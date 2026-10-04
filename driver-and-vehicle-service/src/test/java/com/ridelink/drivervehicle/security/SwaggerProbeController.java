package com.ridelink.drivervehicle.security;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SwaggerProbeController {

    @GetMapping("/v3/api-docs/security-test")
    Map<String, String> apiDocsProbe() {
        return Map.of("status", "available");
    }
}
