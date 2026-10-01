package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.application.port.out.LoginThrottleRepositoryPort;
import in.sherlock.auth.domain.model.LoginThrottle;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class LoginThrottlePersistenceAdapter implements LoginThrottleRepositoryPort {
    private final LoginThrottleJpaRepository repository;

    LoginThrottlePersistenceAdapter(LoginThrottleJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<LoginThrottle> findById(String key) { return repository.findById(key); }
    @Override public Optional<LoginThrottle> findByKeyForUpdate(String key) { return repository.findByKeyForUpdate(key); }
    @Override public LoginThrottle save(LoginThrottle throttle) { return repository.save(throttle); }
    @Override public void deleteById(String key) { repository.deleteById(key); }
}
