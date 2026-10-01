package in.sherlock.auth.domain.service;

import in.sherlock.auth.domain.exception.AuthException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> policy.validate("CorrectHorse9!", "person@example.com"));
    }

    @Test
    void rejectsWeakAndEmailDerivedPasswords() {
        assertThrows(AuthException.class, () -> policy.validate("password123", "person@example.com"));
        assertThrows(AuthException.class, () -> policy.validate("PersonSecure9!", "person@example.com"));
    }
}
