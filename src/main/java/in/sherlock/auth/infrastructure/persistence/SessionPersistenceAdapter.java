package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.domain.model.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SessionPersistenceAdapter implements SessionRepositoryPort {
    private final SessionJpaRepository repository;

    SessionPersistenceAdapter(SessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override public AuthSession save(AuthSession session) { return repository.save(session); }
    @Override public Optional<AuthSession> findById(UUID id) { return repository.findById(id); }
    @Override public Optional<AuthSession> findByIdForUpdate(UUID id) { return repository.findByIdForUpdate(id); }
    @Override public List<AuthSession> findAllByUserIdNewestFirst(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
    @Override public int revokeAllByUserId(UUID userId, Instant now) { return repository.revokeAllByUserId(userId, now); }
}
