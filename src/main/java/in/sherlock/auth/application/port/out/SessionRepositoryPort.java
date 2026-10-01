package in.sherlock.auth.application.port.out;

import in.sherlock.auth.domain.model.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepositoryPort {
    AuthSession save(AuthSession session);
    Optional<AuthSession> findById(UUID id);
    Optional<AuthSession> findByIdForUpdate(UUID id);
    List<AuthSession> findAllByUserIdNewestFirst(UUID userId);
    int revokeAllByUserId(UUID userId, Instant now);
}
