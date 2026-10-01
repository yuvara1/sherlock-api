package in.sherlock.auth.application.service;

import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.UserAccount;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AccountLookup {
    private final UserRepositoryPort users;

    AccountLookup(UserRepositoryPort users) {
        this.users = users;
    }

    UserAccount require(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new AuthException(AuthException.UNAUTHORIZED, "USER_NOT_FOUND", "The user no longer exists."));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
