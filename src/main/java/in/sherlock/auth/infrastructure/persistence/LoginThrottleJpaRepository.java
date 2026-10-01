package in.sherlock.auth.infrastructure.persistence;

import in.sherlock.auth.domain.model.LoginThrottle;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginThrottleJpaRepository extends JpaRepository<LoginThrottle, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LoginThrottle t where t.keyHash = :key")
    Optional<LoginThrottle> findByKeyForUpdate(@Param("key") String key);
}
