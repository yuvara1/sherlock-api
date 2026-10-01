package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.port.out.AuditLogRepositoryPort;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.AuditLog;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AuditRecorder {
    private final AuditLogRepositoryPort auditLogs;

    AuditRecorder(AuditLogRepositoryPort auditLogs) {
        this.auditLogs = auditLogs;
    }

    void record(UUID userId, AuditEvent event, RequestContext context) {
        auditLogs.save(new AuditLog(userId, event, context.ipAddress(), context.userAgent()));
    }
}
