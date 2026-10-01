package in.sherlock.auth.application.port.out;

import in.sherlock.auth.domain.model.Organization;
import java.util.Optional;

public interface OrganizationRepositoryPort {
    Organization save(Organization organization);
    Optional<Organization> findBySlug(String slug);
    Optional<Organization> findById(java.util.UUID id);
}
