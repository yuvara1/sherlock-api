package in.sherlock.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.sherlock.project.application.port.IdentityProvider;
import in.sherlock.project.application.port.InvitationNotifier;
import in.sherlock.project.domain.ProjectModels.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean IdentityProvider identities;
    @MockitoBean InvitationNotifier invitations;
    @MockitoBean JwtDecoder decoder;
    UUID organizationId;
    UUID userId;
    Identity owner;
    static final String INTERNAL_TOKEN = "test-internal-token-32-characters-minimum";

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM organizations");
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Organization organization = new Organization(organizationId, "Acme", "acme-" + organizationId, "FREE", 30, "UTC", "US_EAST_1", "production", Instant.now());
        owner = new Identity(userId, organizationId, "owner@example.com", "Owner", Role.OWNER, true, Instant.now(), organization);
        when(identities.current(anyString())).thenReturn(owner);
    }

    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject(userId.toString()).claim("orgId", organizationId.toString()).issuedAt(Instant.now())));
    }
    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return json.readTree(mvc.perform(authenticated(request)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    JsonNode createProject(String name) throws Exception {
        return call(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ProjectChange(name, List.of("production", "staging"), "Payments", "Java", null))), 201);
    }
    JsonNode createKey(String projectId, String environment) throws Exception {
        return call(post("/api/v1/projects/" + projectId + "/api-keys").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"SDK\",\"environment\":\"" + environment + "\"}"), 201);
    }

    @Test void workspaceSettingsAndPaginatedProjectLifecycle() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        JsonNode organization = call(get("/api/v1/org"), 200);
        assertThat(organization.get("name").asText()).isEqualTo("Acme");
        call(patch("/api/v1/org").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sherlock SRE\",\"timezone\":\"America/New_York\",\"dataRetentionDays\":90}"), 200);
        String projectId = createProject("Payments").get("id").asText();
        createProject("Storefront");
        JsonNode page = call(get("/api/v1/projects?page=1&pageSize=1"), 200);
        assertThat(page.get("total").asInt()).isEqualTo(2);
        assertThat(page.get("hasMore").asBoolean()).isTrue();
        assertThat(page.get("data").size()).isEqualTo(1);
        call(patch("/api/v1/projects/" + projectId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"), 200);
        assertThat(call(get("/api/v1/projects?status=ARCHIVED"), 200).get("total").asInt()).isEqualTo(1);
        mvc.perform(authenticated(delete("/api/v1/projects/" + projectId))).andExpect(status().isNoContent());
        mvc.perform(authenticated(get("/api/v1/projects/" + projectId))).andExpect(status().isNotFound());
    }

    @Test void apiKeysAreOneTimeHashedEnvironmentScopedAndRevocable() throws Exception {
        String projectId = createProject("Payments").get("id").asText();
        JsonNode created = createKey(projectId, "production");
        String plaintext = created.get("plaintext").asText();
        assertThat(plaintext).startsWith("demo_live_");
        assertThat(createKey(projectId, "staging").get("plaintext").asText()).startsWith("demo_test_");
        assertThat(jdbc.queryForObject("SELECT key_hash FROM api_keys WHERE id=?", String.class, UUID.fromString(created.at("/apiKey/id").asText())))
                .hasSize(64).isNotEqualTo(plaintext);
        String listed = mvc.perform(authenticated(get("/api/v1/api-keys"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain(plaintext, "keyHash", "plaintext");
        validateKey(plaintext, 200);
        assertThat(jdbc.queryForObject("SELECT last_used_at FROM api_keys WHERE id=?", java.sql.Timestamp.class, UUID.fromString(created.at("/apiKey/id").asText()))).isNotNull();
        mvc.perform(authenticated(delete("/api/v1/projects/" + projectId + "/api-keys/" + created.at("/apiKey/id").asText()))).andExpect(status().isNoContent());
        validateKey(plaintext, 401);
        JsonNode staging = createKey(projectId, "staging");
        jdbc.update("UPDATE api_keys SET expires_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), UUID.fromString(staging.at("/apiKey/id").asText()));
        validateKey(staging.get("plaintext").asText(), 401);
    }

    void validateKey(String plaintext, int expected) throws Exception {
        mvc.perform(post("/internal/api-keys/validate").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("apiKey", plaintext)))).andExpect(status().is(expected));
    }

    @Test void tenantIsolationAndRevokedMembershipFailClosed() throws Exception {
        String projectId = createProject("Private").get("id").asText();
        UUID otherOrganization = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Organization other = new Organization(otherOrganization, "Other", "other", "FREE", 30, "UTC", "US_EAST_1", "production", Instant.now());
        when(identities.current(anyString())).thenReturn(new Identity(otherUser, otherOrganization, "other@example.com", "Other", Role.OWNER, false, Instant.now(), other));
        userId = otherUser; organizationId = otherOrganization;
        mvc.perform(authenticated(get("/api/v1/projects/" + projectId))).andExpect(status().isNotFound());
        assertThat(call(get("/api/v1/api-keys"), 200).size()).isZero();
        jdbc.update("DELETE FROM team_members WHERE user_id=?", userId);
        mvc.perform(authenticated(get("/api/v1/org"))).andExpect(status().isForbidden());
    }

    @Test void viewerCannotMutateAndOwnerCannotBeRemoved() throws Exception {
        call(get("/api/v1/org"), 200);
        UUID ownerMemberId = jdbc.queryForObject("SELECT id FROM team_members WHERE user_id=?", UUID.class, userId);
        mvc.perform(authenticated(delete("/api/v1/team/" + ownerMemberId))).andExpect(status().isForbidden());
        jdbc.update("UPDATE team_members SET role='VIEWER' WHERE user_id=?", userId);
        when(identities.current(anyString())).thenReturn(new Identity(userId, organizationId, owner.email(), owner.name(), Role.VIEWER, true, Instant.now(), owner.organization()));
        mvc.perform(authenticated(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Forbidden\",\"environments\":[\"production\"]}"))).andExpect(status().isForbidden());
    }

    @Test void validationRejectsInvalidSettingsAndUnavailableExternalProviders() throws Exception {
        call(get("/api/v1/org"), 200);
        call(patch("/api/v1/org").contentType(MediaType.APPLICATION_JSON).content("{\"dataRetentionDays\":366}"), 400);
        call(patch("/api/v1/org").contentType(MediaType.APPLICATION_JSON).content("{\"timezone\":\"Not/AZone\"}"), 400);
        call(patch("/api/v1/org/security").contentType(MediaType.APPLICATION_JSON).content("{\"ipAllowlist\":[\"hostname.example\"]}"), 400);
        call(patch("/api/v1/org/security").contentType(MediaType.APPLICATION_JSON).content("{\"samlSsoEnabled\":true}"), 501);
        call(post("/api/v1/org/subscription/upgrade").contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"), 501);
        createProject("Unique");
        mvc.perform(authenticated(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unique\",\"environments\":[\"production\"]}"))).andExpect(status().isConflict());
        mvc.perform(authenticated(get("/api/v1/org/audit-log/export"))).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"));
        mvc.perform(post("/internal/api-keys/validate").contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"demo_live_bad\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/org")).andExpect(status().isUnauthorized());
    }

    @Test void inviteAcceptanceChecksEmailAndPreventsReplay() throws Exception {
        call(post("/api/v1/team/invite").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"developer@example.com\",\"role\":\"MEMBER\"}"), 201);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(invitations).send(eq("developer@example.com"), eq("Acme"), token.capture());
        UUID invitedUser = UUID.randomUUID();
        UUID sourceOrganization = UUID.randomUUID();
        Organization source = new Organization(sourceOrganization, "New", "new", "FREE", 30, "UTC", "US_EAST_1", "production", Instant.now());
        Identity invitee = new Identity(invitedUser, sourceOrganization, "wrong@example.com", "Developer", Role.OWNER, false, Instant.now(), source);
        when(identities.current(anyString())).thenReturn(invitee);
        userId = invitedUser; organizationId = sourceOrganization;
        call(post("/api/v1/team/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("token", token.getValue()))), 403);
        when(identities.current(anyString())).thenReturn(new Identity(invitedUser, sourceOrganization, "developer@example.com", "Developer", Role.OWNER, false, Instant.now(), source));
        JsonNode member = call(post("/api/v1/team/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("token", token.getValue()))), 200);
        assertThat(member.get("role").asText()).isEqualTo("DEVELOPER");
        verify(identities).assign(eq(invitedUser), eq(owner.organizationId()), eq(Role.DEVELOPER));
        call(post("/api/v1/team/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("token", token.getValue()))), 403);
    }

    @Test void projectDeletionCascadesKeysAndArchiveBlocksIngest() throws Exception {
        String projectId = createProject("Payments").get("id").asText();
        String plaintext = createKey(projectId, "production").get("plaintext").asText();
        call(patch("/api/v1/projects/" + projectId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"), 200);
        validateKey(plaintext, 401);
        mvc.perform(authenticated(delete("/api/v1/projects/" + projectId))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_keys", Integer.class)).isZero();
    }

    @Test void securityPoliciesCheckActualSessionAndIgnoreSpoofedIpHeaders() throws Exception {
        call(get("/api/v1/org"), 200);
        call(patch("/api/v1/org/security").contentType(MediaType.APPLICATION_JSON).content("{\"ipAllowlist\":[\"127.0.0.1/32\"],\"mfaEnforced\":true,\"sessionTimeout\":\"H1\"}"), 200);
        mvc.perform(authenticated(get("/api/v1/org").header("X-Client-IP", "127.0.0.1").with(request -> { request.setRemoteAddr("192.0.2.10"); return request; }))).andExpect(status().isForbidden());
        when(identities.current(anyString())).thenReturn(new Identity(userId, organizationId, owner.email(), owner.name(), Role.OWNER, true, Instant.now().minusSeconds(7200), owner.organization()));
        mvc.perform(authenticated(get("/api/v1/org"))).andExpect(status().isUnauthorized());
    }

    @Test void teamRoleUpdatesAndRemovalSynchronizeWithAuth() throws Exception {
        call(get("/api/v1/org"), 200);
        UUID memberId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO team_members(id,organization_id,user_id,email,name,role,joined_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                memberId, organizationId, memberUserId, "dev@example.com", "Developer", "DEVELOPER");
        assertThat(call(patch("/api/v1/team/" + memberId + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"viewer\"}"), 200).get("role").asText()).isEqualTo("VIEWER");
        verify(identities).assign(memberUserId, organizationId, Role.VIEWER);
        mvc.perform(authenticated(delete("/api/v1/team/" + memberId))).andExpect(status().isNoContent());
        verify(identities).revoke(memberUserId, organizationId);
        assertThat(call(get("/api/v1/team"), 200).size()).isEqualTo(1);
    }

    @Test void removingWorkspaceCascadesServiceOwnedData() throws Exception {
        String projectId = createProject("Payments").get("id").asText();
        createKey(projectId, "production");
        mvc.perform(authenticated(delete("/api/v1/org"))).andExpect(status().isNoContent());
        verify(identities).revoke(userId, organizationId);
        for (String table : List.of("organizations", "projects", "team_members", "api_keys", "security_settings", "subscriptions", "project_audit_logs")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
    }

    @Test void invalidEnvironmentExpiryAndIdentityClaimsAreRejected() throws Exception {
        String projectId = createProject("Payments").get("id").asText();
        call(post("/api/v1/projects/" + projectId + "/api-keys").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"SDK\",\"environment\":\"development\"}"), 400);
        call(post("/api/v1/projects/" + projectId + "/api-keys").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"SDK\",\"environment\":\"production\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"), 400);
        createKey(projectId, "production");
        call(patch("/api/v1/projects/" + projectId).contentType(MediaType.APPLICATION_JSON).content("{\"environments\":[\"staging\"]}"), 400);
        mvc.perform(get("/api/v1/org").with(jwt().jwt(token -> token.subject(userId.toString()).claim("orgId", UUID.randomUUID().toString())))).andExpect(status().isForbidden());
    }
}
