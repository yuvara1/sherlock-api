package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.*;
import in.sherlock.auth.application.port.in.RegisterUseCase;
import in.sherlock.auth.application.port.out.OrganizationRepositoryPort;
import in.sherlock.auth.application.port.out.PasswordHasherPort;
import in.sherlock.auth.application.port.out.SecureTokenPort;
import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.Organization;
import in.sherlock.auth.domain.model.UserAccount;
import in.sherlock.auth.domain.service.PasswordPolicy;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistrationService implements RegisterUseCase {
    private final UserRepositoryPort users;
    private final OrganizationRepositoryPort organizations;
    private final PasswordHasherPort passwords;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenPort secureTokens;
    private final SessionIssuer sessionIssuer;
    private final AuditRecorder audit;

    RegistrationService(UserRepositoryPort users, OrganizationRepositoryPort organizations,
            PasswordHasherPort passwords, PasswordPolicy passwordPolicy, SecureTokenPort secureTokens,
            SessionIssuer sessionIssuer, AuditRecorder audit) {
        this.users = users;
        this.organizations = organizations;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.secureTokens = secureTokens;
        this.sessionIssuer = sessionIssuer;
        this.audit = audit;
    }

    @Override
    @Transactional
    public AuthResult register(RegisterCommand command, RequestContext context) {
        String email = AccountLookup.normalizeEmail(command.email());
        passwordPolicy.validate(command.password(), email);
        if (users.existsByEmail(email)) {
            throw new AuthException(AuthException.CONFLICT, "EMAIL_EXISTS",
                    "An account with that email address already exists.");
        }
        String organizationName = command.organizationName().trim();
        Organization organization = organizations.save(new Organization(
                organizationName, slug(organizationName) + "-" + secureTokens.randomToken(4).toLowerCase(Locale.ROOT)));
        UserAccount user = users.insert(new UserAccount(
                email, command.name().trim(), passwords.hash(command.password()), organization.getId()));
        TokensResult tokens = sessionIssuer.createSession(user, context);
        audit.record(user.getId(), AuditEvent.REGISTER, context);
        return new AuthResult(UserResult.from(user), tokens, OrganizationResult.from(organization));
    }

    private static String slug(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return value.isBlank() ? "organization" : value.substring(0, Math.min(value.length(), 90));
    }
}
