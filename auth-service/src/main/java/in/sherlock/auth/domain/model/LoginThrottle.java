package in.sherlock.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "login_throttles")
public class LoginThrottle {
    @Id
    @Column(length = 64)
    private String keyHash;
    @Column(nullable = false)
    private int failures;
    @Column(nullable = false)
    private Instant windowStartedAt;
    private Instant lockedUntil;

    protected LoginThrottle() {}

    public LoginThrottle(String keyHash, Instant now) {
        this.keyHash = keyHash;
        this.windowStartedAt = now;
    }

    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    public int getFailures() { return failures; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void fail(Instant now) {
        if (windowStartedAt.plusSeconds(300).isBefore(now)) {
            failures = 0;
            lockedUntil = null;
            windowStartedAt = now;
        }
        failures++;
        if (failures >= 5) lockedUntil = now.plusSeconds(300);
    }
}
