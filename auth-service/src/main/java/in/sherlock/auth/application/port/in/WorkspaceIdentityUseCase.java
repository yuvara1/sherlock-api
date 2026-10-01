package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.OrganizationResult;
import in.sherlock.auth.application.dto.UserResult;
import in.sherlock.auth.domain.model.Role;
import java.time.Instant;
import java.util.UUID;

public interface WorkspaceIdentityUseCase {
    WorkspaceIdentity workspace(UUID userId, UUID sessionId);
    void membership(UUID userId, UUID organizationId, Role role, boolean revoked);
    record WorkspaceIdentity(UserResult user, OrganizationResult organization, Instant sessionStartedAt, java.util.List<UserResult> members) {}
}
