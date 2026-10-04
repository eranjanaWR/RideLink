package com.ridelink.farepayment.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalServiceAuthenticator {

    private final byte[] configuredKey;

    public InternalServiceAuthenticator(
            @Value("${security.internal.service-key:}") String configuredKey
    ) {
        this.configuredKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String candidate) {
        if (configuredKey.length == 0 || candidate == null || candidate.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey,
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
