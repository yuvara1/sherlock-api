package in.sherlock.project.application.port;

import in.sherlock.project.domain.ProjectModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Optional<Organization> organization(UUID id);
    boolean bootstrap(Organization organization, Member owner);
    void saveOrganization(Organization organization);
    void lockOrganization(UUID id);
    void deleteOrganization(UUID id);
    List<Project> projects(UUID organizationId, int offset, int limit, String search, String status);
    long projectCount(UUID organizationId, String search, String status);
    Optional<Project> project(UUID organizationId, UUID id);
    void insertProject(Project project);
    void saveProject(Project project);
    void deleteProject(UUID organizationId, UUID id);
    List<Member> members(UUID organizationId);
    Optional<Member> member(UUID organizationId, UUID userId);
    void saveMember(Member member);
    void deleteMember(UUID organizationId, UUID id);
    List<ApiKey> keys(UUID organizationId, UUID projectId);
    Optional<ApiKey> keyByHash(String hash);
    void insertKey(ApiKey key, String hash);
    int revokeKey(UUID organizationId, UUID projectId, UUID keyId);
    int touchKey(UUID keyId, Instant now);
    Invitation invite(Invitation invitation, String hash);
    Optional<Invitation> invitationByHash(String hash);
    void acceptInvitation(UUID id);
    SecuritySettings security(UUID organizationId);
    void saveSecurity(SecuritySettings security);
    Subscription subscription(UUID organizationId);
    void audit(UUID organizationId, UUID userId, String action, Instant now);
    String auditCsv(UUID organizationId, Instant since);
}
