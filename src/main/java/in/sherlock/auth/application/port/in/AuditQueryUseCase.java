package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface AuditQueryUseCase {
    PageResult<AuditResult> auditLog(UUID userId, int page, int pageSize);
}
