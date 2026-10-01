package in.sherlock.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 64)
    private String refreshTokenHash;
    @Column(nullable = false, length = 500)
    private String deviceInfo;
    @Column(nullable = false, length = 64)
    private String ipAddress;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;
    @Version
    private long version;

    protected AuthSession() {}

    public AuthSession(UUID userId, String tokenHash, String deviceInfo, String ipAddress, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.refreshTokenHash = tokenHash;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void rotate(String tokenHash, Instant expiresAt) {
        this.refreshTokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
    public void revoke() {
        if (revokedAt == null) revokedAt = Instant.now();
    }
}
