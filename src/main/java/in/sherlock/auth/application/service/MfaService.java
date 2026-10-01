package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.MfaSetupResult;
import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.port.in.MfaUseCase;
import in.sherlock.auth.application.port.out.EncryptionPort;
import in.sherlock.auth.application.port.out.PasswordHasherPort;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.application.port.out.TotpPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.UserAccount;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MfaService implements MfaUseCase {
    private final TotpPort totp;
    private final EncryptionPort encryption;
    private final PasswordHasherPort passwords;
    private final SessionRepositoryPort sessions;
    private final AccountLookup accounts;
    private final AuditRecorder audit;

    MfaService(TotpPort totp, EncryptionPort encryption, PasswordHasherPort passwords,
            SessionRepositoryPort sessions, AccountLookup accounts, AuditRecorder audit) {
        this.totp = totp;
        this.encryption = encryption;
        this.passwords = passwords;
        this.sessions = sessions;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Override
    @Transactional
    public MfaSetupResult beginMfa(UUID userId) {
        UserAccount user = accounts.require(userId);
        String secret = totp.newSecret();
        user.beginMfaSetup(encryption.encrypt(secret));
        String label = URLEncoder.encode("Sherlock:" + user.getEmail(), StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("Sherlock", StandardCharsets.UTF_8);
        return new MfaSetupResult(
                "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer
                        + "&algorithm=SHA1&digits=6&period=30",
                secret);
    }

    @Override
    @Transactional
    public void verifyMfa(UUID userId, String code, RequestContext context) {
        UserAccount user = accounts.require(userId);
        if (user.getPendingMfaSecretEncrypted() == null
                || !totp.verify(encryption.decrypt(user.getPendingMfaSecretEncrypted()), code)) {
            throw new AuthException(AuthException.BAD_REQUEST, "INVALID_MFA_CODE",
                    "The verification code is invalid or expired.");
        }
        user.enableMfa();
        audit.record(userId, AuditEvent.MFA_ENABLED, context);
    }

    @Override
    @Transactional
    public void disableMfa(UUID userId, String password, String code, RequestContext context) {
        UserAccount user = accounts.require(userId);
        if (!user.isMfaEnabled()
                || !passwords.matches(password, user.getPasswordHash())
                || !totp.verify(encryption.decrypt(user.getMfaSecretEncrypted()), code)) {
            throw new AuthException(AuthException.UNAUTHORIZED, "MFA_DISABLE_FAILED",
                    "The password or verification code is incorrect.");
        }
        user.disableMfa();
        sessions.revokeAllByUserId(userId, Instant.now());
        audit.record(userId, AuditEvent.MFA_DISABLED, context);
    }
}
