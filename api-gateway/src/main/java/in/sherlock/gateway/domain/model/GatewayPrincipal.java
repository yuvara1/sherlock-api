package in.sherlock.gateway.domain.model;

import java.util.Objects;
import java.util.UUID;

public record GatewayPrincipal(UUID userId, UUID organizationId, String role) {
    public GatewayPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(organizationId, "organizationId");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
    }
}
