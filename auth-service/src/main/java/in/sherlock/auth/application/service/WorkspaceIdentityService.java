package in.sherlock.auth.application.service;

import in.sherlock.auth.application.dto.OrganizationResult;
import in.sherlock.auth.application.dto.UserResult;
import in.sherlock.auth.application.port.in.WorkspaceIdentityUseCase;
import in.sherlock.auth.application.port.out.OrganizationRepositoryPort;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.application.port.out.UserRepositoryPort;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.Role;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceIdentityService implements WorkspaceIdentityUseCase {
    private final UserRepositoryPort users;
    private final OrganizationRepositoryPort organizations;
    private final SessionRepositoryPort sessions;
    WorkspaceIdentityService(UserRepositoryPort users, OrganizationRepositoryPort organizations, SessionRepositoryPort sessions) {
        this.users = users; this.organizations = organizations; this.sessions = sessions;
    }
    @Override @Transactional(readOnly=true)
    public WorkspaceIdentity workspace(UUID userId, UUID sessionId) {
        var user = users.findById(userId).orElseThrow(WorkspaceIdentityService::missing);
        var organization = organizations.findById(user.getOrganizationId()).orElseThrow(WorkspaceIdentityService::missing);
        var session = sessions.findById(sessionId).filter(value -> value.getUserId().equals(userId) && value.isActive(Instant.now())).orElseThrow(WorkspaceIdentityService::missing);
        return new WorkspaceIdentity(UserResult.from(user), OrganizationResult.from(organization), session.getCreatedAt(),
                users.findByOrganizationId(user.getOrganizationId()).stream().map(UserResult::from).toList());
    }
    @Override @Transactional
    public void membership(UUID userId, UUID organizationId, Role role, boolean revoked) {
        var user = users.findById(userId).orElseThrow(WorkspaceIdentityService::missing);
        organizations.findById(organizationId).orElseThrow(WorkspaceIdentityService::missing);
        if (revoked && !user.getOrganizationId().equals(organizationId)) return;
        user.changeMembership(organizationId, revoked ? Role.VIEWER : role);
        users.save(user);
        sessions.revokeAllByUserId(userId, Instant.now());
    }
    private static AuthException missing() { return new AuthException(404, "NOT_FOUND", "Account, organization, or session not found."); }
}
