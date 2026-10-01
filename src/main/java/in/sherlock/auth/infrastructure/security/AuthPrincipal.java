package in.sherlock.auth.infrastructure.security;

import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID sessionId, String role) {
}
