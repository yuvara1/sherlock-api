package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.dto.TokensResult;
import in.sherlock.auth.application.port.in.TokenRefreshUseCase;
import in.sherlock.auth.application.port.out.AuthSettingsPort;
import in.sherlock.auth.application.port.out.SecureTokenPort;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.AuthSession;
import in.sherlock.auth.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TokenRefreshService implements TokenRefreshUseCase {
    private final SessionRepositoryPort sessions;
    private final SecureTokenPort secureTokens;
    private final TokenProviderPort tokens;
    private final AuthSettingsPort settings;
    private final SessionIssuer sessionIssuer;
    private final AccountLookup accounts;
    private final AuditRecorder audit;

    TokenRefreshService(SessionRepositoryPort sessions, SecureTokenPort secureTokens, TokenProviderPort tokens,
            AuthSettingsPort settings, SessionIssuer sessionIssuer, AccountLookup accounts, AuditRecorder audit) {
        this.sessions = sessions;
        this.secureTokens = secureTokens;
        this.tokens = tokens;
        this.settings = settings;
        this.sessionIssuer = sessionIssuer;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Override
    @Transactional
    public TokensResult refresh(String refreshToken, RequestContext context) {
        AuthSession session = sessions.findByIdForUpdate(sessionIdOf(refreshToken))
                .orElseThrow(TokenRefreshService::invalidRefreshToken);
        if (!session.isActive(Instant.now())
                || !secureTokens.hashMatches(refreshToken, session.getRefreshTokenHash())) {
            throw invalidRefreshToken();
        }
        UserAccount user = accounts.require(session.getUserId());
        String rotated = sessionIssuer.newRefreshToken(session.getId());
        session.rotate(secureTokens.sha256(rotated), Instant.now().plus(settings.refreshTtl()));
        audit.record(user.getId(), AuditEvent.TOKEN_REFRESH, context);
        return new TokensResult(tokens.accessToken(user, session.getId()), rotated, tokens.accessExpiresInSeconds());
    }

    private static UUID sessionIdOf(String token) {
        try {
            int separator = token.indexOf('.');
            if (separator < 1 || token.length() > 200) throw invalidRefreshToken();
            return UUID.fromString(token.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private static AuthException invalidRefreshToken() {
        return new AuthException(AuthException.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "The refresh token is invalid or expired.");
    }
}
