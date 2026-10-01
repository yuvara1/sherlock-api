package in.sherlock.auth.infrastructure.security;

import in.sherlock.auth.application.dto.AccessTokenClaims;
import in.sherlock.auth.application.port.out.PublicKeyProviderPort;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.UserAccount;
import in.sherlock.auth.infrastructure.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements TokenProviderPort, PublicKeyProviderPort {
    private final AuthProperties properties;
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtTokenProvider(AuthProperties properties) {
        this.properties = properties;
        try {
            KeyFactory keys = KeyFactory.getInstance("RSA");
            this.privateKey = keys.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(properties.jwtPrivateKeyBase64())));
            this.publicKey = (RSAPublicKey) keys.generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(properties.jwtPublicKeyBase64())));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "JWT keys must be a matching Base64-encoded PKCS#8 private key and X.509 public key", e);
        }
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
                .header().keyId(properties.jwtKeyId()).and()
                .signWith(privateKey, Jwts.SIG.RS256)
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
                .header().keyId(properties.jwtKeyId()).and()
                .signWith(privateKey, Jwts.SIG.RS256)
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

    @Override
    public Map<String, Object> jwk() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", properties.jwtKeyId());
        key.put("n", unsignedBase64Url(publicKey.getModulus()));
        key.put("e", unsignedBase64Url(publicKey.getPublicExponent()));
        return Map.copyOf(key);
    }

    private Claims parse(String token, String requiredType) {
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey).requireIssuer(properties.issuer())
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

    private static String unsignedBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
