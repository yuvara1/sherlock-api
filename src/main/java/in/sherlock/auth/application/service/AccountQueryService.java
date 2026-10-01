package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.AuditResult;
import in.sherlock.auth.application.dto.PageResult;
import in.sherlock.auth.application.dto.UserResult;
import in.sherlock.auth.application.port.in.AuditQueryUseCase;
import in.sherlock.auth.application.port.in.UserQueryUseCase;
import in.sherlock.auth.application.port.out.AuditLogRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountQueryService implements UserQueryUseCase, AuditQueryUseCase {
    private final AccountLookup accounts;
    private final AuditLogRepositoryPort auditLogs;

    AccountQueryService(AccountLookup accounts, AuditLogRepositoryPort auditLogs) {
        this.accounts = accounts;
        this.auditLogs = auditLogs;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResult me(UUID userId) {
        return UserResult.from(accounts.require(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditResult> auditLog(UUID userId, int page, int pageSize) {
        return auditLogs.findByUserId(userId, page, Math.min(pageSize, 100)).map(AuditResult::from);
    }
}
