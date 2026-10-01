package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.domain.model.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationJpaRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
}
