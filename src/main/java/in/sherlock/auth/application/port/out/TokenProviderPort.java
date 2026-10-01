package in.sherlock.auth.application.port.out;

import in.sherlock.auth.application.dto.AccessTokenClaims;
import in.sherlock.auth.domain.model.UserAccount;
import java.util.UUID;

public interface TokenProviderPort {
    String accessToken(UserAccount user, UUID sessionId);
    String mfaToken(UserAccount user);
    /** Parses and validates an access token, throwing INVALID_TOKEN on failure. */
    AccessTokenClaims parseAccessToken(String token);
    /** Parses and validates an MFA challenge token, returning the user id. */
    UUID mfaUserId(String token);
    long accessExpiresInSeconds();
}
