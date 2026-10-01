package in.sherlock.auth.application.dto;

import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditResult(UUID id, AuditEvent event, String ipAddress, String userAgent, Instant timestamp) {
    public static AuditResult from(AuditLog log) {
        return new AuditResult(log.getId(), log.getEvent(), log.getIpAddress(), log.getUserAgent(), log.getTimestamp());
    }
}
