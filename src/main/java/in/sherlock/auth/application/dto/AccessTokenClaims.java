package in.sherlock.auth.application.dto;

import java.util.UUID;

public record AccessTokenClaims(UUID userId, UUID sessionId, String role) {}
