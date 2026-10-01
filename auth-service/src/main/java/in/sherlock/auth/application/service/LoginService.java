package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.*;
import in.sherlock.auth.application.port.in.LoginUseCase;
import in.sherlock.auth.application.port.out.EncryptionPort;
import in.sherlock.auth.application.port.out.PasswordHasherPort;
import in.sherlock.auth.application.port.out.SecureTokenPort;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.application.port.out.TotpPort;
import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LoginService implements LoginUseCase {
    private static final String INVALID_CREDENTIALS = "The email or password is incorrect.";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwords;
    private final TokenProviderPort tokens;
    private final EncryptionPort encryption;
    private final TotpPort totp;
    private final LoginThrottleService throttle;
    private final SessionIssuer sessionIssuer;
    private final AccountLookup accounts;
    private final AuditRecorder audit;
    private final String dummyPasswordHash;

    LoginService(UserRepositoryPort users, PasswordHasherPort passwords, TokenProviderPort tokens,
            EncryptionPort encryption, TotpPort totp, LoginThrottleService throttle, SessionIssuer sessionIssuer,
            AccountLookup accounts, AuditRecorder audit, SecureTokenPort secureTokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.encryption = encryption;
        this.totp = totp;
        this.throttle = throttle;
        this.sessionIssuer = sessionIssuer;
        this.accounts = accounts;
        this.audit = audit;
        // Used to equalise timing when the account does not exist.
        this.dummyPasswordHash = passwords.hash(secureTokens.randomToken(24));
    }

    @Override
    @Transactional
    public LoginResult login(LoginCommand command, RequestContext context) {
        String email = AccountLookup.normalizeEmail(command.email());
        throttle.check(email, context.ipAddress());
        UserAccount user = users.findByEmail(email).orElse(null);
        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        if (!passwords.matches(command.password(), storedHash) || user == null) {
            throttle.failed(email, context.ipAddress());
            audit.record(null, AuditEvent.LOGIN_FAILED, context);
            throw new AuthException(AuthException.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        throttle.succeeded(email, context.ipAddress());
        if (user.isMfaEnabled()) {
            return LoginResult.mfaPending(tokens.mfaToken(user));
        }
        TokensResult issued = sessionIssuer.createSession(user, context);
        audit.record(user.getId(), AuditEvent.LOGIN, context);
        return LoginResult.authenticated(user, issued);
    }

    @Override
    @Transactional
    public LoginResult completeMfaLogin(MfaLoginCommand command, RequestContext context) {
        UserAccount user = accounts.require(tokens.mfaUserId(command.mfaToken()));
        if (!user.isMfaEnabled()
                || !totp.verify(encryption.decrypt(user.getMfaSecretEncrypted()), command.code())) {
            throw new AuthException(AuthException.UNAUTHORIZED, "INVALID_MFA_CODE",
                    "The verification code is invalid or expired.");
        }
        TokensResult issued = sessionIssuer.createSession(user, context);
        audit.record(user.getId(), AuditEvent.LOGIN, context);
        return LoginResult.authenticated(user, issued);
    }
}
