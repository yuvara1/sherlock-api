package in.sherlock.gateway.application.service;

import in.sherlock.gateway.application.port.in.ResolvePrincipalUseCase;
import in.sherlock.gateway.domain.exception.InvalidIdentityException;
import in.sherlock.gateway.domain.model.GatewayPrincipal;
import java.util.Map;
import java.util.UUID;

public final class ResolvePrincipalService implements ResolvePrincipalUseCase {
    @Override
    public GatewayPrincipal resolve(Map<String, Object> claims) {
        try {
            return new GatewayPrincipal(
                    UUID.fromString(required(claims, "sub")),
                    UUID.fromString(required(claims, "orgId")),
                    required(claims, "role"));
        } catch (RuntimeException exception) {
            throw new InvalidIdentityException("The access token is missing required identity claims", exception);
        }
    }

    private static String required(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
