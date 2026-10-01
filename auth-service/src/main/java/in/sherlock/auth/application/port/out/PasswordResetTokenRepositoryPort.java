package in.sherlock.auth.application.port.out;

import in.sherlock.auth.domain.model.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepositoryPort {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void deleteAllByUserId(UUID userId);
}
