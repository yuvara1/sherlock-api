package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.port.out.PasswordResetTokenRepositoryPort;
import in.sherlock.auth.domain.model.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepositoryPort {
    private final PasswordResetTokenJpaRepository repository;

    PasswordResetTokenPersistenceAdapter(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override public PasswordResetToken save(PasswordResetToken token) { return repository.save(token); }
    @Override public Optional<PasswordResetToken> findByTokenHash(String hash) { return repository.findByTokenHash(hash); }
    @Override public void deleteAllByUserId(UUID userId) { repository.deleteAllByUserId(userId); }
}
