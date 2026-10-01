package in.sherlock.auth.application.service;

import in.sherlock.auth.application.port.out.LoginThrottleRepositoryPort;
import in.sherlock.auth.application.port.out.SecureTokenPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.LoginThrottle;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable five-attempt / five-minute lockout per email + IP pair. */
@Service
public class LoginThrottleService {
    private final LoginThrottleRepositoryPort repository;
    private final SecureTokenPort secureTokens;

    public LoginThrottleService(LoginThrottleRepositoryPort repository, SecureTokenPort secureTokens) {
        this.repository = repository;
        this.secureTokens = secureTokens;
    }

    @Transactional(readOnly = true)
    public void check(String email, String ipAddress) {
        repository.findById(key(email, ipAddress)).ifPresent(throttle -> {
            if (throttle.isLocked(Instant.now())) {
                throw new AuthException(AuthException.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                        "Too many sign-in attempts. Try again in five minutes.");
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String email, String ipAddress) {
        String key = key(email, ipAddress);
        LoginThrottle throttle = repository.findByKeyForUpdate(key)
                .orElseGet(() -> new LoginThrottle(key, Instant.now()));
        throttle.fail(Instant.now());
        repository.save(throttle);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(String email, String ipAddress) {
        repository.deleteById(key(email, ipAddress));
    }

    private String key(String email, String ipAddress) {
        return secureTokens.sha256(email + "\n" + ipAddress);
    }
}
