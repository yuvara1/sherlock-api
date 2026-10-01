package in.sherlock.auth.infrastructure.seed;

import in.sherlock.auth.application.port.out.EncryptionPort;
import in.sherlock.auth.application.port.out.OrganizationRepositoryPort;
import in.sherlock.auth.application.port.out.PasswordHasherPort;
import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.model.Organization;
import in.sherlock.auth.domain.model.Role;
import in.sherlock.auth.domain.model.UserAccount;
import in.sherlock.auth.domain.service.PasswordPolicy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates demo accounts for local development. Enabled with {@code sherlock.seed.enabled=true}
 * (on in the {@code dev} profile, off by default). Idempotent: existing accounts are left untouched.
 */
@Component
@ConditionalOnProperty(name = "sherlock.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String ORGANIZATION_NAME = "Sherlock Demo";
    public static final String ORGANIZATION_SLUG = "sherlock-demo";
    public static final String MFA_SECRET = "JBSWY3DPEHPK3PXP";

    public record DemoAccount(String email, String name, String password, Role role, boolean mfa) {}

    public static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("admin@sherlock.dev", "Sherlock Admin", "Sherlock@Owner123", Role.OWNER, false),
            new DemoAccount("demo@sherlock.dev", "Demo Developer", "Sherlock@Guest123", Role.DEVELOPER, false),
            new DemoAccount("mfa@sherlock.dev", "MFA Demo User", "Sherlock@Mfa1234", Role.DEVELOPER, true));

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepositoryPort users;
    private final OrganizationRepositoryPort organizations;
    private final PasswordHasherPort passwords;
    private final PasswordPolicy passwordPolicy;
    private final EncryptionPort encryption;
    private final TransactionTemplate transactions;

    public DemoDataSeeder(UserRepositoryPort users, OrganizationRepositoryPort organizations,
            PasswordHasherPort passwords, PasswordPolicy passwordPolicy, EncryptionPort encryption,
            TransactionTemplate transactions) {
        this.users = users;
        this.organizations = organizations;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.encryption = encryption;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> seed());
    }

    void seed() {
        Organization organization = organizations.findBySlug(ORGANIZATION_SLUG)
                .orElseGet(() -> organizations.save(new Organization(ORGANIZATION_NAME, ORGANIZATION_SLUG)));
        for (DemoAccount account : ACCOUNTS) {
            if (users.existsByEmail(account.email())) {
                LOG.info("Demo account {} already exists; skipping", account.email());
                continue;
            }
            passwordPolicy.validate(account.password(), account.email());
            UserAccount user = new UserAccount(account.email(), account.name(),
                    passwords.hash(account.password()), organization.getId(), account.role());
            if (account.mfa()) {
                user.beginMfaSetup(encryption.encrypt(MFA_SECRET));
                user.enableMfa();
            }
            users.save(user);
            LOG.warn("Seeded demo account {} ({}){}", account.email(), account.role(),
                    account.mfa() ? " with TOTP MFA" : "");
        }
    }
}
