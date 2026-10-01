package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.domain.model.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findAllByUserId(UUID userId, Pageable pageable);
}
