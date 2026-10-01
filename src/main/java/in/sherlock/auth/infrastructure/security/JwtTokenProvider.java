package in.sherlock.auth.infrastructure.security;

import in.sherlock.auth.application.dto.AccessTokenClaims;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.UserAccount;
import in.sherlock.auth.infrastructure.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements TokenProviderPort {
    private final AuthProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(AuthProperties properties) {
        this.properties = properties;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.jwtSecretBase64());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT secret must be Base64 encoded", e);
        }
        if (decoded.length < 64) throw new IllegalStateException("JWT secret must contain at least 64 random bytes");
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    @Override
    public String accessToken(UserAccount user, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim("type", "access")
                .claim("sid", sessionId.toString())
                .claim("orgId", user.getOrganizationId().toString())
                .claim("role", user.getRole().name())
                .claim("env", "production")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    @Override
    public String mfaToken(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim("type", "mfa")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.mfaTtl())))
                .signWith(key)
                .compact();
    }

    @Override
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = parse(token, "access");
        try {
            return new AccessTokenClaims(UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("sid", String.class)), claims.get("role", String.class));
        } catch (RuntimeException e) {
            throw invalidToken();
        }
    }

    @Override
    public UUID mfaUserId(String token) {
        try {
            return UUID.fromString(parse(token, "mfa").getSubject());
        } catch (AuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidToken();
        }
    }

    @Override
    public long accessExpiresInSeconds() {
        return properties.accessTtl().toSeconds();
    }

    private Claims parse(String token, String requiredType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer())
                    .build().parseSignedClaims(token).getPayload();
            if (!requiredType.equals(claims.get("type", String.class))) throw invalidToken();
            return claims;
        } catch (AuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidToken();
        }
    }

    private static AuthException invalidToken() {
        return new AuthException(AuthException.UNAUTHORIZED, "INVALID_TOKEN",
                "The authentication token is invalid or expired.");
    }
}
