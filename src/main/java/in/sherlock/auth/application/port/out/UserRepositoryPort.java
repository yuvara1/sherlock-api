package in.sherlock.auth.application.port.out;

import in.sherlock.auth.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    Optional<UserAccount> findById(UUID id);
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    /** Inserts a new user, throwing EMAIL_EXISTS if the email is already taken. */
    UserAccount insert(UserAccount user);
    UserAccount save(UserAccount user);
}
