package in.sherlock.auth.application.dto;

import in.sherlock.auth.domain.model.AuthSession;
import java.time.Instant;
import java.util.UUID;

public record SessionResult(
        UUID id, String deviceInfo, String ipAddress, Instant createdAt, Instant expiresAt, boolean current) {
    public static SessionResult from(AuthSession session, UUID currentId) {
        return new SessionResult(session.getId(), session.getDeviceInfo(), session.getIpAddress(),
                session.getCreatedAt(), session.getExpiresAt(), session.getId().equals(currentId));
    }
}
