package in.sherlock.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private boolean mfaEnabled;

    @Column(length = 512)
    private String mfaSecretEncrypted;

    @Column(length = 512)
    private String pendingMfaSecretEncrypted;

    @Column(length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String email, String name, String passwordHash, UUID organizationId) {
        this(email, name, passwordHash, organizationId, Role.OWNER);
    }

    public UserAccount(String email, String name, String passwordHash, UUID organizationId, Role role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.organizationId = organizationId;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UUID getOrganizationId() { return organizationId; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public String getMfaSecretEncrypted() { return mfaSecretEncrypted; }
    public String getPendingMfaSecretEncrypted() { return pendingMfaSecretEncrypted; }
    public String getAvatarUrl() { return avatarUrl; }
    public Instant getCreatedAt() { return createdAt; }

    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
    public void beginMfaSetup(String encryptedSecret) { this.pendingMfaSecretEncrypted = encryptedSecret; }
    public void enableMfa() {
        this.mfaSecretEncrypted = pendingMfaSecretEncrypted;
        this.pendingMfaSecretEncrypted = null;
        this.mfaEnabled = true;
    }
    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaSecretEncrypted = null;
        this.pendingMfaSecretEncrypted = null;
    }
}
