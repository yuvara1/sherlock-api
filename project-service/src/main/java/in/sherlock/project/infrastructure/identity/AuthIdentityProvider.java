package in.sherlock.project.infrastructure.identity;

import in.sherlock.project.application.port.IdentityProvider;
import in.sherlock.project.domain.ProjectException;
import in.sherlock.project.domain.ProjectModels.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuthIdentityProvider implements IdentityProvider {
    private final RestClient client;
    private final String internalToken;

    public AuthIdentityProvider(@Value("${sherlock.auth-url}") String baseUrl,
            @Value("${sherlock.internal-token}") String internalToken) {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalToken = internalToken;
    }

    @Override public Identity current(String bearerToken) {
        try {
            Workspace result = client.get().uri("/api/v1/auth/workspace").header("Authorization", bearerToken).retrieve().body(Workspace.class);
            if (result == null) throw new IllegalStateException("Empty identity response");
            Account user = result.user();
            WorkspaceOrganization organization = result.organization();
            return new Identity(user.id(), user.organizationId(), user.email(), user.name(), Role.parse(user.role()), user.mfaEnabled(), result.sessionStartedAt(),
                    new Organization(organization.id(), organization.name(), organization.slug(), organization.plan(), 30,
                            "UTC", "US_EAST_1", "production", organization.createdAt()));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) throw new ProjectException(401, "SESSION_REVOKED", "The authentication session is no longer active.");
            throw unavailable();
        } catch (RestClientException exception) { throw unavailable(); }
    }

    @Override public void assign(UUID userId, UUID organizationId, Role role) {
        call("/internal/project/users/" + userId + "/membership", Map.of("organizationId", organizationId, "role", role.name(), "revoked", false));
    }
    @Override public java.util.List<Member> initialMembers(String bearerToken) {
        try {
            Workspace result = client.get().uri("/api/v1/auth/workspace").header("Authorization", bearerToken).retrieve().body(Workspace.class);
            if (result == null || result.members() == null) throw unavailable();
            return result.members().stream().map(account -> new Member(UUID.randomUUID(), account.organizationId(), account.id(),
                    account.email(), account.name(), Role.parse(account.role()), account.createdAt())).toList();
        } catch (RestClientException exception) { throw unavailable(); }
    }
    @Override public void revoke(UUID userId, UUID organizationId) {
        call("/internal/project/users/" + userId + "/membership", Map.of("organizationId", organizationId, "role", "VIEWER", "revoked", true));
    }
    private void call(String path, Map<String, Object> body) {
        try { client.post().uri(path).header("X-Internal-Token", internalToken).body(body).retrieve().toBodilessEntity(); }
        catch (RestClientException exception) { throw unavailable(); }
    }
    private static ProjectException unavailable() { return new ProjectException(503, "AUTH_UNAVAILABLE", "Authentication service is unavailable."); }
    public record Workspace(Account user, WorkspaceOrganization organization, Instant sessionStartedAt, java.util.List<Account> members) {}
    public record Account(UUID id, String email, String name, String role, UUID organizationId, boolean mfaEnabled, Instant createdAt) {}
    public record WorkspaceOrganization(UUID id, String name, String slug, String plan, Instant createdAt) {}
}
