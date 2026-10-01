package in.sherlock.auth.domain.service;

import in.sherlock.auth.domain.exception.AuthException;
import java.util.List;

/** Pure domain rule describing acceptable account passwords. */
public class PasswordPolicy {
    private static final List<String> COMMON_PASSWORDS = List.of(
            "password", "password1", "password123", "123456789", "qwerty123",
            "letmein123", "admin123", "welcome123");

    public void validate(String password, String email) {
        boolean valid = password.length() >= 12
                && password.length() <= 128
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        String lowered = password.toLowerCase();
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase();
        if (!valid || COMMON_PASSWORDS.contains(lowered) || (localPart.length() >= 4 && lowered.contains(localPart))) {
            throw new AuthException(AuthException.BAD_REQUEST, "WEAK_PASSWORD",
                    "Use 12–128 characters with upper and lowercase letters, a number, and a symbol.");
        }
    }
}
