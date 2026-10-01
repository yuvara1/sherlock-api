package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {
    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<UserAccount> findById(UUID id) { return repository.findById(id); }
    @Override public Optional<UserAccount> findByEmail(String email) { return repository.findByEmail(email); }
    @Override public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
    @Override public java.util.List<UserAccount> findByOrganizationId(UUID organizationId) { return repository.findByOrganizationId(organizationId); }
    @Override public UserAccount save(UserAccount user) { return repository.save(user); }

    @Override
    public UserAccount insert(UserAccount user) {
        try {
            return repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(AuthException.CONFLICT, "EMAIL_EXISTS",
                    "An account with that email address already exists.");
        }
    }
}
