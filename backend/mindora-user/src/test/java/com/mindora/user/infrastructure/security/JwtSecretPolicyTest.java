package com.mindora.user.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JwtSecretPolicyTest {
    private static final String VALID = "stage-3.6a-valid-secret-0123456789abcdef";

    @Test
    void keepsValidSecretUntouched() {
        JwtSecretPolicy.ResolvedSecret resolved = JwtSecretPolicy.resolve(VALID, false);

        assertEquals(VALID, resolved.value());
        assertFalse(resolved.generated());
    }

    @Test
    void rejectsMissingShortAndPlaceholderSecretsOutsideLocal() {
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.resolve("", false));
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.resolve(null, false));
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.resolve("short-secret", false));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.resolve("change-me-to-a-long-random-stage0-secret", false));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.resolve("local-stage0-secret", false));
    }

    @Test
    void generatesEphemeralSecretForLocalWhenUnset() {
        JwtSecretPolicy.ResolvedSecret first = JwtSecretPolicy.resolve("", true);
        JwtSecretPolicy.ResolvedSecret second = JwtSecretPolicy.resolve("   ", true);

        assertTrue(first.generated());
        assertTrue(first.value().length() >= JwtSecretPolicy.MIN_LENGTH);
        assertNotEquals(first.value(), second.value());
    }

    @Test
    void generatesEphemeralSecretForLocalPlaceholder() {
        JwtSecretPolicy.ResolvedSecret resolved =
                JwtSecretPolicy.resolve("local-stage0-secret", true);

        assertTrue(resolved.generated());
        assertTrue(resolved.value().length() >= JwtSecretPolicy.MIN_LENGTH);
    }

    @Test
    void stillRejectsExplicitlyShortSecretEvenInLocal() {
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.resolve("short-secret", true));
    }
}
