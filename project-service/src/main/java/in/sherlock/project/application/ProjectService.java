package in.sherlock.project.application;

import in.sherlock.project.application.port.*;
import in.sherlock.project.domain.ProjectException;
import in.sherlock.project.domain.ProjectModels.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ProjectService {
    private static final Set<String> ENVIRONMENTS = Set.of("production", "staging", "development");
    private static final Set<String> REGIONS = Set.of("US_EAST_1", "US_WEST_2", "EU_WEST_1", "AP_SOUTHEAST_1");
    private final ProjectRepository repository;
    private final IdentityProvider identities;
    private final SecretProvider secrets;
    private final InvitationNotifier invitations;
    private final AccessPolicy policy;
    private final Clock clock;

    public ProjectService(ProjectRepository repository, IdentityProvider identities, SecretProvider secrets,
            InvitationNotifier invitations, AccessPolicy policy, Clock clock) {
        this.repository = repository;
        this.identities = identities;
        this.secrets = secrets;
        this.invitations = invitations;
        this.policy = policy;
        this.clock = clock;
    }

    private Member authorize(Actor actor) {
        Identity identity = identities.current(actor.bearerToken());
        if (!actor.userId().equals(identity.id()) || !actor.organizationId().equals(identity.organizationId())) {
            throw ProjectException.forbidden();
        }
        if (repository.organization(actor.organizationId()).isEmpty()) {
            if (identity.role() != Role.OWNER) throw ProjectException.forbidden();
            if (repository.bootstrap(identity.organization(), new Member(UUID.randomUUID(), actor.organizationId(),
                    actor.userId(), identity.email(), identity.name(), Role.OWNER, clock.instant()))) {
                repository.lockOrganization(actor.organizationId());
                for (Member initialMember : identities.initialMembers(actor.bearerToken())) {
                    if (initialMember.organizationId().equals(actor.organizationId()) && repository.member(actor.organizationId(), initialMember.userId()).isEmpty()) {
                        repository.saveMember(initialMember);
                    }
                }
            }
        }
        Member member = repository.member(actor.organizationId(), actor.userId()).orElseThrow(ProjectException::forbidden);
        if (member.role() != identity.role()) throw ProjectException.forbidden();
        policy.enforce(repository.security(actor.organizationId()), actor, identity);
        return member;
    }

    private Member manage(Actor actor) {
        authorize(actor);
        repository.lockOrganization(actor.organizationId());
        Member member = repository.member(actor.organizationId(), actor.userId()).orElseThrow(ProjectException::forbidden);
        if (!member.role().manages()) throw ProjectException.forbidden();
        return member;
    }

    private void owner(Actor actor) {
        authorize(actor);
        repository.lockOrganization(actor.organizationId());
        if (repository.member(actor.organizationId(), actor.userId()).orElseThrow(ProjectException::forbidden).role() != Role.OWNER) throw ProjectException.forbidden();
    }

    private void write(Actor actor) {
        authorize(actor);
        repository.lockOrganization(actor.organizationId());
        if (!repository.member(actor.organizationId(), actor.userId()).orElseThrow(ProjectException::forbidden).role().writes()) throw ProjectException.forbidden();
    }

    public Organization organization(Actor actor) {
        authorize(actor);
        return repository.organization(actor.organizationId()).orElseThrow(ProjectException::missing);
    }

    public Organization updateOrganization(Actor actor, OrganizationChange change) {
        manage(actor);
        Organization previous = repository.organization(actor.organizationId()).orElseThrow(ProjectException::missing);
        String timezone = change.timezone() == null ? previous.timezone() : change.timezone();
        try { ZoneId.of(timezone); } catch (RuntimeException exception) { throw ProjectException.invalid("Invalid IANA timezone."); }
        int retention = change.dataRetentionDays() == null ? previous.dataRetentionDays() : change.dataRetentionDays();
        if (retention < 7 || retention > 365) throw ProjectException.invalid("Data retention must be between 7 and 365 days.");
        String region = change.region() == null ? previous.region() : change.region().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!REGIONS.contains(region)) throw ProjectException.invalid("Invalid region.");
        String environment = change.defaultEnvironment() == null ? previous.defaultEnvironment() : change.defaultEnvironment();
        environment(environment);
        Organization updated = new Organization(previous.id(), change.name() == null ? previous.name() : text(change.name(), 100),
                previous.slug(), previous.plan(), retention, timezone, region, environment, previous.createdAt());
        repository.saveOrganization(updated);
        audit(actor, "ORGANIZATION_UPDATED");
        return updated;
    }

    public void deleteOrganization(Actor actor) {
        owner(actor);
        repository.lockOrganization(actor.organizationId());
        for (Member member : repository.members(actor.organizationId())) identities.revoke(member.userId(), actor.organizationId());
        repository.deleteOrganization(actor.organizationId());
    }

    public Page<Project> projects(Actor actor, int page, int pageSize, String search, String status) {
        authorize(actor);
        if (page < 1 || pageSize < 1 || pageSize > 100 || page > 1_000_000) throw ProjectException.invalid("Invalid pagination.");
        if (search.length() > 100) throw ProjectException.invalid("Search is too long.");
        if (!status.isEmpty() && !Set.of("ACTIVE", "ARCHIVED").contains(status)) throw ProjectException.invalid("Invalid project status.");
        long total = repository.projectCount(actor.organizationId(), search, status);
        return new Page<>(repository.projects(actor.organizationId(), (page - 1) * pageSize, pageSize, search, status),
                total, page, pageSize, (long) page * pageSize < total);
    }

    public Project project(Actor actor, UUID id) {
        authorize(actor);
        return requireProject(actor.organizationId(), id);
    }

    public Project createProject(Actor actor, ProjectChange change) {
        write(actor);
        String name = text(change.name(), 100);
        validateEnvironments(change.environments());
        Project project = new Project(UUID.randomUUID(), actor.organizationId(), name, slug(name),
                optionalText(change.description(), 2000), optionalText(change.language(), 50), "ACTIVE", change.environments(), clock.instant());
        repository.insertProject(project);
        audit(actor, "PROJECT_CREATED:" + project.id());
        return project;
    }

    public Project updateProject(Actor actor, UUID id, ProjectChange change) {
        write(actor);
        Project previous = requireProject(actor.organizationId(), id);
        String name = change.name() == null ? previous.name() : text(change.name(), 100);
        List<String> environments = change.environments() == null ? previous.environments() : change.environments();
        validateEnvironments(environments);
        String status = change.status() == null ? previous.status() : change.status();
        if (!Set.of("ACTIVE", "ARCHIVED").contains(status)) throw ProjectException.invalid("Invalid project status.");
        if (!environments.containsAll(repository.keys(actor.organizationId(), id).stream()
                .filter(key -> key.status().equals("ACTIVE")).map(ApiKey::environment).toList())) {
            throw ProjectException.invalid("Revoke API keys before removing their environment.");
        }
        Project updated = new Project(id, previous.organizationId(), name, slug(name),
                change.description() == null ? previous.description() : optionalText(change.description(), 2000),
                change.language() == null ? previous.language() : optionalText(change.language(), 50), status, environments, previous.createdAt());
        repository.saveProject(updated);
        audit(actor, "PROJECT_UPDATED:" + id);
        return updated;
    }

    public void deleteProject(Actor actor, UUID id) {
        manage(actor);
        requireProject(actor.organizationId(), id);
        repository.deleteProject(actor.organizationId(), id);
        audit(actor, "PROJECT_DELETED:" + id);
    }

    public List<ApiKey> keys(Actor actor, UUID projectId) {
        authorize(actor);
        if (projectId != null) requireProject(actor.organizationId(), projectId);
        return repository.keys(actor.organizationId(), projectId);
    }

    public CreatedKey createKey(Actor actor, UUID projectId, String name, String environment, Instant expiresAt) {
        write(actor);
        Project project = requireProject(actor.organizationId(), projectId);
        environment(environment);
        if (!project.status().equals("ACTIVE") || !project.environments().contains(environment)) {
            throw ProjectException.invalid("The project must be active and contain the key environment.");
        }
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) throw ProjectException.invalid("Expiry must be in the future.");
        String prefix = environment.equals("production") ? "demo_live_" : "demo_test_";
        String plaintext = prefix + secrets.generate();
        ApiKey key = new ApiKey(UUID.randomUUID(), projectId, actor.organizationId(), text(name, 100), prefix, environment,
                "ACTIVE", expiresAt, actor.userId(), clock.instant(), null);
        repository.insertKey(key, secrets.hash(plaintext));
        audit(actor, "API_KEY_CREATED:" + key.id());
        return new CreatedKey(key, plaintext);
    }

    public void revokeKey(Actor actor, UUID projectId, UUID keyId) {
        write(actor);
        requireProject(actor.organizationId(), projectId);
        if (repository.revokeKey(actor.organizationId(), projectId, keyId) == 0) throw ProjectException.missing();
        audit(actor, "API_KEY_REVOKED:" + keyId);
    }

    public ValidatedKey validateKey(String plaintext) {
        if (plaintext == null || plaintext.length() > 256 ||
                !(plaintext.startsWith("demo_live_") || plaintext.startsWith("demo_test_"))) throw invalidKey();
        ApiKey key = repository.keyByHash(secrets.hash(plaintext)).orElseThrow(ProjectService::invalidKey);
        Instant now = clock.instant();
        Project project = requireProject(key.organizationId(), key.projectId());
        if (!key.status().equals("ACTIVE") || (key.expiresAt() != null && !key.expiresAt().isAfter(now)) ||
                !project.status().equals("ACTIVE") || !project.environments().contains(key.environment())) throw invalidKey();
        if (repository.touchKey(key.id(), now) == 0) throw invalidKey();
        return new ValidatedKey(key.organizationId(), key.projectId(), key.environment(), key.id());
    }

    public List<Member> members(Actor actor) { authorize(actor); return repository.members(actor.organizationId()); }

    public Invitation invite(Actor actor, String email, String roleText) {
        Member actorMember = manage(actor);
        Role role = role(roleText);
        if (role == Role.OWNER || (role == Role.ADMIN && actorMember.role() != Role.OWNER)) throw ProjectException.forbidden();
        String normalized = text(email, 254).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw ProjectException.invalid("Invalid email.");
        if (repository.members(actor.organizationId()).stream().anyMatch(member -> member.email().equals(normalized))) {
            throw new ProjectException(409, "ALREADY_MEMBER", "This email is already a team member.");
        }
        String token = secrets.generate();
        Invitation invitation = new Invitation(UUID.randomUUID(), actor.organizationId(), normalized, role, "PENDING",
                clock.instant().plus(Duration.ofDays(7)), clock.instant());
        repository.invite(invitation, secrets.hash(token));
        invitations.send(normalized, repository.organization(actor.organizationId()).orElseThrow(ProjectException::missing).name(), token);
        audit(actor, "TEAM_INVITED:" + invitation.id());
        return invitation;
    }

    public Member acceptInvitation(Actor actor, String token) {
        Identity identity = identities.current(actor.bearerToken());
        if (!identity.id().equals(actor.userId())) throw ProjectException.forbidden();
        String tokenHash = secrets.hash(text(token, 256));
        Invitation invitation = repository.invitationByHash(tokenHash).orElseThrow(ProjectException::missing);
        repository.lockOrganization(invitation.organizationId());
        invitation = repository.invitationByHash(tokenHash).orElseThrow(ProjectException::missing);
        if (!invitation.status().equals("PENDING") || !invitation.expiresAt().isAfter(clock.instant()) ||
                !invitation.email().equalsIgnoreCase(identity.email())) throw ProjectException.forbidden();
        if (identity.role() == Role.OWNER && !identity.organizationId().equals(invitation.organizationId())) {
            if (repository.organization(identity.organizationId()).isPresent()) {
                repository.lockOrganization(identity.organizationId());
                if (repository.projectCount(identity.organizationId(), "", "") > 0 || repository.members(identity.organizationId()).size() > 1) {
                    throw new ProjectException(409, "OWNER_CANNOT_MOVE", "An owner with projects or teammates cannot leave their workspace.");
                }
                repository.deleteOrganization(identity.organizationId());
            }
        } else if (!identity.organizationId().equals(invitation.organizationId())) {
            repository.member(identity.organizationId(), identity.id()).ifPresent(previous ->
                    repository.deleteMember(previous.organizationId(), previous.id()));
        }
        if (repository.member(invitation.organizationId(), identity.id()).isPresent()) {
            throw new ProjectException(409, "ALREADY_MEMBER", "You are already a team member.");
        }
        Member member = new Member(UUID.randomUUID(), invitation.organizationId(), identity.id(), identity.email(), identity.name(), invitation.role(), clock.instant());
        identities.assign(identity.id(), invitation.organizationId(), invitation.role());
        repository.saveMember(member);
        repository.acceptInvitation(invitation.id());
        repository.audit(invitation.organizationId(), identity.id(), "INVITATION_ACCEPTED", clock.instant());
        return member;
    }

    public Member changeRole(Actor actor, UUID memberId, String roleText) {
        Member manager = manage(actor);
        repository.lockOrganization(actor.organizationId());
        Member target = memberById(actor.organizationId(), memberId);
        Role role = role(roleText);
        if (target.userId().equals(actor.userId()) || target.role() == Role.OWNER || role == Role.OWNER ||
                (manager.role() != Role.OWNER && (target.role() == Role.ADMIN || role == Role.ADMIN))) throw ProjectException.forbidden();
        Member updated = new Member(target.id(), target.organizationId(), target.userId(), target.email(), target.name(), role, target.joinedAt());
        identities.assign(target.userId(), target.organizationId(), role);
        repository.saveMember(updated);
        audit(actor, "TEAM_ROLE_CHANGED:" + memberId);
        return updated;
    }

    public void removeMember(Actor actor, UUID memberId) {
        Member manager = manage(actor);
        repository.lockOrganization(actor.organizationId());
        Member target = memberById(actor.organizationId(), memberId);
        if (target.userId().equals(actor.userId()) || target.role() == Role.OWNER ||
                (target.role() == Role.ADMIN && manager.role() != Role.OWNER)) throw ProjectException.forbidden();
        identities.revoke(target.userId(), actor.organizationId());
        repository.deleteMember(actor.organizationId(), memberId);
        audit(actor, "TEAM_REMOVED:" + memberId);
    }

    public SecuritySettings security(Actor actor) { manage(actor); return repository.security(actor.organizationId()); }

    public SecuritySettings updateSecurity(Actor actor, SecurityChange change) {
        owner(actor);
        SecuritySettings previous = repository.security(actor.organizationId());
        if (Boolean.TRUE.equals(change.samlSsoEnabled())) {
            throw new ProjectException(501, "SAML_NOT_CONFIGURED", "SAML sign-in requires an identity-provider integration.");
        }
        List<String> cidrs = change.ipAllowlist() == null ? previous.ipAllowlist() : change.ipAllowlist();
        policy.validateCidrs(cidrs);
        String timeout = change.sessionTimeout() == null ? previous.sessionTimeout() : switch (change.sessionTimeout()) {
            case "1h" -> "H1";
            case "8h" -> "H8";
            case "24h" -> "H24";
            case "7d" -> "D7";
            default -> change.sessionTimeout();
        };
        if (!Set.of("H1", "H8", "H24", "D7").contains(timeout)) throw ProjectException.invalid("Invalid session timeout.");
        SecuritySettings updated = new SecuritySettings(actor.organizationId(), false,
                change.samlIdpMetadataUrl() == null ? previous.samlIdpMetadataUrl() : optionalText(change.samlIdpMetadataUrl(), 2000),
                change.mfaEnforced() == null ? previous.mfaEnforced() : change.mfaEnforced(), cidrs, timeout,
                change.auditLogEnabled() == null ? previous.auditLogEnabled() : change.auditLogEnabled());
        policy.enforce(updated, actor, identities.current(actor.bearerToken()));
        repository.saveSecurity(updated);
        audit(actor, "SECURITY_UPDATED");
        return updated;
    }

    public String auditCsv(Actor actor) {
        manage(actor);
        return repository.auditCsv(actor.organizationId(), clock.instant().minus(Duration.ofDays(90)));
    }

    public Subscription subscription(Actor actor) { manage(actor); return repository.subscription(actor.organizationId()); }

    public Object upgrade(Actor actor, String plan) {
        owner(actor);
        if (!Set.of("PRO", "ENTERPRISE").contains(plan)) throw ProjectException.invalid("Invalid upgrade plan.");
        throw new ProjectException(501, "BILLING_NOT_CONFIGURED", "A billing provider must be configured before creating checkout sessions.");
    }

    private Member memberById(UUID organizationId, UUID memberId) {
        return repository.members(organizationId).stream().filter(member -> member.id().equals(memberId)).findFirst().orElseThrow(ProjectException::missing);
    }
    private Project requireProject(UUID organizationId, UUID id) { return repository.project(organizationId, id).orElseThrow(ProjectException::missing); }
    private void audit(Actor actor, String event) { repository.audit(actor.organizationId(), actor.userId(), event, clock.instant()); }
    private static ProjectException invalidKey() { return new ProjectException(401, "INVALID_API_KEY", "The API key is invalid, revoked, or expired."); }
    private static Role role(String value) {
        try { return Role.parse(value); } catch (RuntimeException exception) { throw ProjectException.invalid("Invalid role."); }
    }
    private static void environment(String value) { if (value == null || !ENVIRONMENTS.contains(value)) throw ProjectException.invalid("Invalid environment."); }
    private static void validateEnvironments(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 3 || values.stream().distinct().count() != values.size()) throw ProjectException.invalid("Provide unique project environments.");
        values.forEach(ProjectService::environment);
    }
    private static String text(String value, int length) {
        if (value == null || value.isBlank() || value.trim().length() > length) throw ProjectException.invalid("A required text field is missing or too long.");
        return value.trim();
    }
    private static String optionalText(String value, int length) {
        if (value != null && value.length() > length) throw ProjectException.invalid("Text field is too long.");
        return value;
    }
    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "project" : slug;
    }
}
