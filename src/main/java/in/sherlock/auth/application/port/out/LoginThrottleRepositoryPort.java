package in.sherlock.auth.application.port.out;

import in.sherlock.auth.domain.model.LoginThrottle;
import java.util.Optional;

public interface LoginThrottleRepositoryPort {
    Optional<LoginThrottle> findById(String keyHash);
    Optional<LoginThrottle> findByKeyForUpdate(String keyHash);
    LoginThrottle save(LoginThrottle throttle);
    void deleteById(String keyHash);
}
