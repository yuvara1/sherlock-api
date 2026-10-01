package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.dto.TokensResult;
import in.sherlock.auth.application.port.out.AuthSettingsPort;
import in.sherlock.auth.application.port.out.SecureTokenPort;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.domain.model.AuthSession;
import in.sherlock.auth.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates sessions and formats opaque refresh tokens ("{sessionId}.{random}"). */
@Component
class SessionIssuer {
    private final SessionRepositoryPort sessions;
    private final TokenProviderPort tokens;
    private final SecureTokenPort secureTokens;
    private final AuthSettingsPort settings;

    SessionIssuer(SessionRepositoryPort sessions, TokenProviderPort tokens,
            SecureTokenPort secureTokens, AuthSettingsPort settings) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.secureTokens = secureTokens;
        this.settings = settings;
    }

    TokensResult createSession(UserAccount user, RequestContext context) {
        AuthSession session = new AuthSession(
                user.getId(), secureTokens.sha256(secureTokens.randomToken(32)), context.userAgent(),
                context.ipAddress(), Instant.now().plus(settings.refreshTtl()));
        session = sessions.save(session);
        String refreshToken = newRefreshToken(session.getId());
        session.rotate(secureTokens.sha256(refreshToken), Instant.now().plus(settings.refreshTtl()));
        return new TokensResult(tokens.accessToken(user, session.getId()), refreshToken,
                tokens.accessExpiresInSeconds());
    }

    String newRefreshToken(UUID sessionId) {
        return sessionId + "." + secureTokens.randomToken(32);
    }
}
