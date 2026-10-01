package in.sherlock.auth.application.port.out;

import in.sherlock.auth.application.dto.PageResult;
import in.sherlock.auth.domain.model.AuditLog;
import java.util.UUID;

public interface AuditLogRepositoryPort {
    AuditLog save(AuditLog log);
    /** Returns the user's audit entries, newest first. */
    PageResult<AuditLog> findByUserId(UUID userId, int page, int pageSize);
}
