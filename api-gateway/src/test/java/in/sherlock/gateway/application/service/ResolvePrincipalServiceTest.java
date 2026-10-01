package in.sherlock.gateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.sherlock.gateway.domain.exception.InvalidIdentityException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolvePrincipalServiceTest {
    private final ResolvePrincipalService service = new ResolvePrincipalService();

    @Test
    void resolvesRequiredTenantIdentityClaims() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        var principal = service.resolve(Map.of(
                "sub", userId.toString(),
                "orgId", organizationId.toString(),
                "role", "OWNER"));

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.organizationId()).isEqualTo(organizationId);
        assertThat(principal.role()).isEqualTo("OWNER");
    }

    @Test
    void rejectsTokensWithoutTenantClaims() {
        assertThatThrownBy(() -> service.resolve(Map.of(
                "sub", UUID.randomUUID().toString(),
                "role", "VIEWER")))
                .isInstanceOf(InvalidIdentityException.class);
    }
}
