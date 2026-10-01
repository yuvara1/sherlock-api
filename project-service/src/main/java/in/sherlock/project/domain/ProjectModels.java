package in.sherlock.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectModels {
    private ProjectModels() {}

    public enum Role {
        OWNER, ADMIN, DEVELOPER, VIEWER;

        public static Role parse(String value) {
            String normalized = value.toUpperCase(java.util.Locale.ROOT);
            return valueOf(normalized.equals("MEMBER") ? "DEVELOPER" : normalized);
        }

        public boolean manages() { return this == OWNER || this == ADMIN; }
        public boolean writes() { return this != VIEWER; }
    }

    public record Actor(UUID userId, UUID organizationId, String bearerToken, Instant issuedAt, String ip) {}
    public record Organization(UUID id, String name, String slug, String plan, int dataRetentionDays,
            String timezone, String region, String defaultEnvironment, Instant createdAt) {}
    public record Project(UUID id, UUID organizationId, String name, String slug, String description,
            String language, String status, List<String> environments, Instant createdAt) {
        public Project { environments = List.copyOf(environments); }
    }
    public record Member(UUID id, UUID organizationId, UUID userId, String email, String name,
            Role role, Instant joinedAt) {}
    public record ApiKey(UUID id, UUID projectId, UUID organizationId, String name, String prefix,
            String environment, String status, Instant expiresAt, UUID createdBy, Instant createdAt,
            Instant lastUsedAt) {}
    public record CreatedKey(ApiKey apiKey, String plaintext) {}
    public record ValidatedKey(UUID organizationId, UUID projectId, String environment, UUID keyId) {}
    public record Invitation(UUID id, UUID organizationId, String email, Role role, String status,
            Instant expiresAt, Instant createdAt) {}
    public record SecuritySettings(UUID organizationId, boolean samlSsoEnabled, String samlIdpMetadataUrl,
            boolean mfaEnforced, List<String> ipAllowlist, String sessionTimeout, boolean auditLogEnabled) {
        public SecuritySettings { ipAllowlist = List.copyOf(ipAllowlist); }
    }
    public record Subscription(UUID organizationId, String plan, int priceMonthly, String status, Instant renewsAt) {}
    public record Identity(UUID id, UUID organizationId, String email, String name, Role role,
            boolean mfaEnabled, Instant sessionStartedAt, Organization organization) {}
    public record Page<T>(List<T> data, long total, int page, int pageSize, boolean hasMore) {}
    public record ProjectChange(String name, List<String> environments, String description, String language, String status) {}
    public record OrganizationChange(String name, String timezone, Integer dataRetentionDays, String region, String defaultEnvironment) {}
    public record SecurityChange(Boolean samlSsoEnabled, String samlIdpMetadataUrl, Boolean mfaEnforced,
            List<String> ipAllowlist, String sessionTimeout, Boolean auditLogEnabled) {}
}
