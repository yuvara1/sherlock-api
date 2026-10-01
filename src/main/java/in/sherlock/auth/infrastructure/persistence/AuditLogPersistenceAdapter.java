package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.dto.PageResult;
import in.sherlock.auth.application.port.out.AuditLogRepositoryPort;
import in.sherlock.auth.domain.model.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class AuditLogPersistenceAdapter implements AuditLogRepositoryPort {
    private final AuditLogJpaRepository repository;

    AuditLogPersistenceAdapter(AuditLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override public AuditLog save(AuditLog log) { return repository.save(log); }

    @Override
    public PageResult<AuditLog> findByUserId(UUID userId, int page, int pageSize) {
        var result = repository.findAllByUserId(userId,
                PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));
        return new PageResult<>(result.getContent(), result.getTotalElements(), page, result.getSize(), result.hasNext());
    }
}
