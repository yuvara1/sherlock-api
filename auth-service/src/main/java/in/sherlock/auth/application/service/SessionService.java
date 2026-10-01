package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.dto.SessionResult;
import in.sherlock.auth.application.port.in.SessionUseCase;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SessionService implements SessionUseCase {
    private final SessionRepositoryPort sessions;
    private final AuditRecorder audit;

    SessionService(SessionRepositoryPort sessions, AuditRecorder audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void logout(UUID userId, UUID sessionId, RequestContext context) {
        sessions.findById(sessionId)
                .filter(session -> session.getUserId().equals(userId))
                .ifPresent(AuthSession::revoke);
        audit.record(userId, AuditEvent.LOGOUT, context);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResult> sessions(UUID userId, UUID currentSessionId) {
        Instant now = Instant.now();
        return sessions.findAllByUserIdNewestFirst(userId).stream()
                .filter(session -> session.isActive(now))
                .map(session -> SessionResult.from(session, currentSessionId))
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, RequestContext context) {
        AuthSession session = sessions.findById(sessionId)
                .filter(value -> value.getUserId().equals(userId))
                .orElseThrow(() -> new AuthException(AuthException.NOT_FOUND, "SESSION_NOT_FOUND",
                        "The session was not found."));
        session.revoke();
        audit.record(userId, AuditEvent.SESSION_REVOKED, context);
    }
}
