package in.sherlock.project.application.port;

import in.sherlock.project.domain.ProjectModels.*;
import java.util.UUID;

public interface IdentityProvider {
    Identity current(String bearerToken);
    java.util.List<Member> initialMembers(String bearerToken);
    void assign(UUID userId, UUID organizationId, Role role);
    void revoke(UUID userId, UUID organizationId);
}
