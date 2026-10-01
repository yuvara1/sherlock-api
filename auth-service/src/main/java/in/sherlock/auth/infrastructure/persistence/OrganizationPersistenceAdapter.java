package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.port.out.OrganizationRepositoryPort;
import in.sherlock.auth.domain.model.Organization;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class OrganizationPersistenceAdapter implements OrganizationRepositoryPort {
    private final OrganizationJpaRepository repository;

    OrganizationPersistenceAdapter(OrganizationJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Organization save(Organization organization) { return repository.save(organization); }
    @Override public Optional<Organization> findBySlug(String slug) { return repository.findBySlug(slug); }
    @Override public Optional<Organization> findById(java.util.UUID id) { return repository.findById(id); }
}
