package com.ridelink.farepayment.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InternalServiceAuthenticatorTest {

    @Test
    void acceptsExactConfiguredKey() {
        InternalServiceAuthenticator authenticator =
                new InternalServiceAuthenticator("test-only-internal-key");

        assertThat(authenticator.isValid("test-only-internal-key")).isTrue();
    }

    @Test
    void rejectsWrongBlankMissingAndUnconfiguredKeys() {
        InternalServiceAuthenticator configured =
                new InternalServiceAuthenticator("test-only-internal-key");
        InternalServiceAuthenticator unconfigured = new InternalServiceAuthenticator("");

        assertThat(configured.isValid("wrong-key")).isFalse();
        assertThat(configured.isValid(" ")).isFalse();
        assertThat(configured.isValid(null)).isFalse();
        assertThat(unconfigured.isValid("test-only-internal-key")).isFalse();
    }
}
