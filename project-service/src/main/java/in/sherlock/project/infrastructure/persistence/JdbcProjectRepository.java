package in.sherlock.project.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.sherlock.project.application.port.ProjectRepository;
import in.sherlock.project.domain.ProjectModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcProjectRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override
    public Optional<Organization> organization(UUID id) {
        return first(jdbc.query("SELECT * FROM organizations WHERE id = ?", ORGANIZATION, id));
    }

    @Override
    public boolean bootstrap(Organization organization, Member owner) {
        int inserted = jdbc.update("""
                INSERT INTO organizations(id,name,slug,plan,data_retention_days,timezone,region,default_environment,created_at)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, organization.id(), organization.name(), organization.slug(), organization.plan(), organization.dataRetentionDays(),
                organization.timezone(), organization.region(), organization.defaultEnvironment(), timestamp(organization.createdAt()));
        if (inserted == 1) {
            saveMember(owner);
            jdbc.update("INSERT INTO security_settings(organization_id) VALUES (?)", organization.id());
            jdbc.update("INSERT INTO subscriptions(organization_id,plan,price_monthly,status) VALUES (?,?,?,?)",
                    organization.id(), organization.plan(), organization.plan().equals("PRO") ? 49 : 0, "ACTIVE");
        }
        return inserted == 1;
    }

    @Override public void lockOrganization(UUID id) { jdbc.queryForObject("SELECT id FROM organizations WHERE id = ? FOR UPDATE", UUID.class, id); }
    @Override public void saveOrganization(Organization organization) {
        jdbc.update("UPDATE organizations SET name=?,timezone=?,data_retention_days=?,region=?,default_environment=? WHERE id=?",
                organization.name(), organization.timezone(), organization.dataRetentionDays(), organization.region(), organization.defaultEnvironment(), organization.id());
    }
    @Override public void deleteOrganization(UUID id) { jdbc.update("DELETE FROM organizations WHERE id=?", id); }

    @Override
    public List<Project> projects(UUID organizationId, int offset, int limit, String search, String status) {
        return jdbc.query("""
                SELECT * FROM projects WHERE organization_id=? AND LOWER(name) LIKE ? ESCAPE '!'
                AND (?='' OR status=?) ORDER BY created_at DESC,id LIMIT ? OFFSET ?
                """, this::project, organizationId, searchPattern(search), status, status, limit, offset);
    }
    @Override public long projectCount(UUID organizationId, String search, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE organization_id=? AND LOWER(name) LIKE ? ESCAPE '!' AND (?='' OR status=?)",
                Long.class, organizationId, searchPattern(search), status, status);
    }
    @Override public Optional<Project> project(UUID organizationId, UUID id) {
        return first(jdbc.query("SELECT * FROM projects WHERE organization_id=? AND id=?", this::project, organizationId, id));
    }
    @Override public void insertProject(Project project) {
        jdbc.update("INSERT INTO projects(id,organization_id,name,slug,description,language,status,environments,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                project.id(), project.organizationId(), project.name(), project.slug(), project.description(), project.language(),
                project.status(), encode(project.environments()), timestamp(project.createdAt()));
    }
    @Override public void saveProject(Project project) {
        jdbc.update("UPDATE projects SET name=?,slug=?,description=?,language=?,status=?,environments=? WHERE organization_id=? AND id=?",
                project.name(), project.slug(), project.description(), project.language(), project.status(), encode(project.environments()), project.organizationId(), project.id());
    }
    @Override public void deleteProject(UUID organizationId, UUID id) { jdbc.update("DELETE FROM projects WHERE organization_id=? AND id=?", organizationId, id); }
    @Override public List<Member> members(UUID organizationId) {
        return jdbc.query("SELECT * FROM team_members WHERE organization_id=? ORDER BY joined_at,id", MEMBER, organizationId);
    }
    @Override public Optional<Member> member(UUID organizationId, UUID userId) {
        return first(jdbc.query("SELECT * FROM team_members WHERE organization_id=? AND user_id=?", MEMBER, organizationId, userId));
    }
    @Override public void saveMember(Member member) {
        int updated = jdbc.update("UPDATE team_members SET role=?,name=?,email=? WHERE organization_id=? AND id=?",
                member.role().name(), member.name(), member.email(), member.organizationId(), member.id());
        if (updated == 0) jdbc.update("INSERT INTO team_members(id,organization_id,user_id,email,name,role,joined_at) VALUES (?,?,?,?,?,?,?)",
                member.id(), member.organizationId(), member.userId(), member.email(), member.name(), member.role().name(), timestamp(member.joinedAt()));
    }
    @Override public void deleteMember(UUID organizationId, UUID id) { jdbc.update("DELETE FROM team_members WHERE organization_id=? AND id=?", organizationId, id); }

    @Override public List<ApiKey> keys(UUID organizationId, UUID projectId) {
        if (projectId == null) return jdbc.query("SELECT * FROM api_keys WHERE organization_id=? ORDER BY created_at DESC,id", API_KEY, organizationId);
        return jdbc.query("SELECT * FROM api_keys WHERE organization_id=? AND project_id=? ORDER BY created_at DESC,id", API_KEY, organizationId, projectId);
    }
    @Override public Optional<ApiKey> keyByHash(String hash) {
        return first(jdbc.query("SELECT * FROM api_keys WHERE key_hash=? FOR UPDATE", API_KEY, hash));
    }
    @Override public void insertKey(ApiKey key, String hash) {
        jdbc.update("INSERT INTO api_keys(id,project_id,organization_id,name,prefix,key_hash,environment,status,expires_at,created_by,created_at,last_used_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                key.id(), key.projectId(), key.organizationId(), key.name(), key.prefix(), hash, key.environment(), key.status(),
                timestamp(key.expiresAt()), key.createdBy(), timestamp(key.createdAt()), timestamp(key.lastUsedAt()));
    }
    @Override public int revokeKey(UUID organizationId, UUID projectId, UUID keyId) {
        return jdbc.update("UPDATE api_keys SET status='REVOKED' WHERE organization_id=? AND project_id=? AND id=?", organizationId, projectId, keyId);
    }
    @Override public int touchKey(UUID keyId, Instant now) {
        return jdbc.update("UPDATE api_keys SET last_used_at=? WHERE id=? AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>?)", timestamp(now), keyId, timestamp(now));
    }
    @Override public Invitation invite(Invitation invitation, String hash) {
        jdbc.update("UPDATE invitations SET status='REVOKED' WHERE organization_id=? AND email=? AND status='PENDING'", invitation.organizationId(), invitation.email());
        jdbc.update("INSERT INTO invitations(id,organization_id,email,role,status,token_hash,expires_at,created_at) VALUES (?,?,?,?,?,?,?,?)",
                invitation.id(), invitation.organizationId(), invitation.email(), invitation.role().name(), invitation.status(), hash,
                timestamp(invitation.expiresAt()), timestamp(invitation.createdAt()));
        return invitation;
    }
    @Override public Optional<Invitation> invitationByHash(String hash) {
        return first(jdbc.query("SELECT * FROM invitations WHERE token_hash=?", INVITATION, hash));
    }
    @Override public void acceptInvitation(UUID id) { jdbc.update("UPDATE invitations SET status='ACCEPTED' WHERE id=?", id); }
    @Override public SecuritySettings security(UUID organizationId) {
        return jdbc.queryForObject("SELECT * FROM security_settings WHERE organization_id=?", (row, index) -> new SecuritySettings(
                uuid(row, "organization_id"), row.getBoolean("saml_sso_enabled"), row.getString("saml_idp_metadata_url"),
                row.getBoolean("mfa_enforced"), decode(row.getString("ip_allowlist")), row.getString("session_timeout"), row.getBoolean("audit_log_enabled")), organizationId);
    }
    @Override public void saveSecurity(SecuritySettings settings) {
        jdbc.update("UPDATE security_settings SET saml_sso_enabled=?,saml_idp_metadata_url=?,mfa_enforced=?,ip_allowlist=?,session_timeout=?,audit_log_enabled=? WHERE organization_id=?",
                settings.samlSsoEnabled(), settings.samlIdpMetadataUrl(), settings.mfaEnforced(), encode(settings.ipAllowlist()), settings.sessionTimeout(), settings.auditLogEnabled(), settings.organizationId());
    }
    @Override public Subscription subscription(UUID organizationId) {
        return jdbc.queryForObject("SELECT * FROM subscriptions WHERE organization_id=?", (row, index) -> new Subscription(
                uuid(row, "organization_id"), row.getString("plan"), row.getInt("price_monthly"), row.getString("status"), instant(row, "renews_at")), organizationId);
    }
    @Override public void audit(UUID organizationId, UUID userId, String action, Instant now) {
        jdbc.update("DELETE FROM project_audit_logs WHERE organization_id=? AND timestamp<?", organizationId, timestamp(now.minus(Duration.ofDays(90))));
        if (security(organizationId).auditLogEnabled()) jdbc.update("INSERT INTO project_audit_logs(id,organization_id,user_id,action,timestamp) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), organizationId, userId, action, timestamp(now));
    }
    @Override public String auditCsv(UUID organizationId, Instant since) {
        List<String> rows = jdbc.query("SELECT * FROM project_audit_logs WHERE organization_id=? AND timestamp>=? ORDER BY timestamp DESC,id",
                (row, index) -> uuid(row, "id") + "," + uuid(row, "user_id") + "," + row.getString("action") + "," + instant(row, "timestamp"), organizationId, timestamp(since));
        return "id,userId,action,timestamp\n" + String.join("\n", rows) + "\n";
    }

    private Project project(ResultSet row, int index) throws SQLException {
        return new Project(uuid(row, "id"), uuid(row, "organization_id"), row.getString("name"), row.getString("slug"),
                row.getString("description"), row.getString("language"), row.getString("status"), decode(row.getString("environments")), instant(row, "created_at"));
    }
    private static final RowMapper<Organization> ORGANIZATION = (row, index) -> new Organization(uuid(row, "id"), row.getString("name"), row.getString("slug"),
            row.getString("plan"), row.getInt("data_retention_days"), row.getString("timezone"), row.getString("region"), row.getString("default_environment"), instant(row, "created_at"));
    private static final RowMapper<Member> MEMBER = (row, index) -> new Member(uuid(row, "id"), uuid(row, "organization_id"), uuid(row, "user_id"),
            row.getString("email"), row.getString("name"), Role.parse(row.getString("role")), instant(row, "joined_at"));
    private static final RowMapper<ApiKey> API_KEY = (row, index) -> new ApiKey(uuid(row, "id"), uuid(row, "project_id"), uuid(row, "organization_id"),
            row.getString("name"), row.getString("prefix"), row.getString("environment"), row.getString("status"), instant(row, "expires_at"),
            uuid(row, "created_by"), instant(row, "created_at"), instant(row, "last_used_at"));
    private static final RowMapper<Invitation> INVITATION = (row, index) -> new Invitation(uuid(row, "id"), uuid(row, "organization_id"), row.getString("email"),
            Role.parse(row.getString("role")), row.getString("status"), instant(row, "expires_at"), instant(row, "created_at"));
    private String encode(List<String> values) {
        try { return json.writeValueAsString(values); } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private List<String> decode(String value) {
        try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private static <T> Optional<T> first(List<T> values) { return values.stream().findFirst(); }
    private static UUID uuid(ResultSet row, String field) throws SQLException { return row.getObject(field, UUID.class); }
    private static Instant instant(ResultSet row, String field) throws SQLException { Timestamp value = row.getTimestamp(field); return value == null ? null : value.toInstant(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String searchPattern(String value) {
        return "%" + value.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
