package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.port.in.PasswordResetUseCase;
import in.sherlock.auth.application.port.out.*;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.PasswordResetToken;
import in.sherlock.auth.domain.model.UserAccount;
import in.sherlock.auth.domain.service.PasswordPolicy;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetService implements PasswordResetUseCase {
    private final UserRepositoryPort users;
    private final PasswordResetTokenRepositoryPort resetTokens;
    private final SessionRepositoryPort sessions;
    private final PasswordHasherPort passwords;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenPort secureTokens;
    private final PasswordResetNotifierPort notifier;
    private final AuthSettingsPort settings;
    private final AccountLookup accounts;
    private final AuditRecorder audit;

    PasswordResetService(UserRepositoryPort users, PasswordResetTokenRepositoryPort resetTokens,
            SessionRepositoryPort sessions, PasswordHasherPort passwords, PasswordPolicy passwordPolicy,
            SecureTokenPort secureTokens, PasswordResetNotifierPort notifier, AuthSettingsPort settings,
            AccountLookup accounts, AuditRecorder audit) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.secureTokens = secureTokens;
        this.notifier = notifier;
        this.settings = settings;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void forgotPassword(String rawEmail, RequestContext context) {
        users.findByEmail(AccountLookup.normalizeEmail(rawEmail)).ifPresent(user -> {
            resetTokens.deleteAllByUserId(user.getId());
            String raw = secureTokens.randomToken(32);
            resetTokens.save(new PasswordResetToken(
                    user.getId(), secureTokens.sha256(raw), Instant.now().plus(settings.resetTtl())));
            notifier.send(user.getEmail(), raw);
            audit.record(user.getId(), AuditEvent.PASSWORD_RESET_REQUESTED, context);
        });
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword, RequestContext context) {
        PasswordResetToken token = resetTokens.findByTokenHash(secureTokens.sha256(rawToken))
                .orElseThrow(PasswordResetService::invalidResetToken);
        if (!token.isUsable(Instant.now())) throw invalidResetToken();
        UserAccount user = accounts.require(token.getUserId());
        passwordPolicy.validate(newPassword, user.getEmail());
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new AuthException(AuthException.BAD_REQUEST, "PASSWORD_REUSED",
                    "The new password must be different from the current password.");
        }
        user.changePassword(passwords.hash(newPassword));
        token.markUsed();
        sessions.revokeAllByUserId(user.getId(), Instant.now());
        audit.record(user.getId(), AuditEvent.PASSWORD_RESET, context);
    }

    private static AuthException invalidResetToken() {
        return new AuthException(AuthException.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "The password reset link is invalid or expired.");
    }
}
