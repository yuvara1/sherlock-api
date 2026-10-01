package in.sherlock.project.presentation;

import in.sherlock.project.application.ProjectService;
import in.sherlock.project.domain.ProjectException;
import in.sherlock.project.domain.ProjectModels.*;
import in.sherlock.project.infrastructure.security.ProjectSecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class ProjectController {
    private final ProjectService service;
    private final String internalToken;
    public ProjectController(ProjectService service, @Value("${sherlock.internal-token}") String internalToken) {
        this.service = service; this.internalToken = internalToken;
    }
    private Actor actor(Jwt jwt, HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            if (ProjectSecurityConfiguration.matches(internalToken, request.getHeader("X-Gateway-Token"))) {
                String forwarded = request.getHeader("X-Client-IP");
                if (forwarded != null && forwarded.matches("[0-9a-fA-F:.]+")) ip = forwarded;
            }
            return new Actor(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("orgId")),
                    "Bearer " + jwt.getTokenValue(), jwt.getIssuedAt(), ip);
        } catch (RuntimeException exception) { throw new ProjectException(401, "INVALID_IDENTITY", "The token is missing required identity claims."); }
    }
    @GetMapping("/org") public Organization organization(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return service.organization(actor(jwt, request)); }
    @PatchMapping("/org") public Organization updateOrganization(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @RequestBody OrganizationChange change) { return service.updateOrganization(actor(jwt, request), change); }
    @DeleteMapping("/org") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteOrganization(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { service.deleteOrganization(actor(jwt, request)); }
    @GetMapping("/projects") public Page<Project> projects(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(defaultValue="") String search, @RequestParam(defaultValue="") String status) { return service.projects(actor(jwt, request), page, pageSize, search, status); }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED) public Project createProject(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @RequestBody ProjectChange change) { return service.createProject(actor(jwt, request), change); }
    @GetMapping("/projects/{id}") public Project project(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id) { return service.project(actor(jwt, request), id); }
    @PatchMapping("/projects/{id}") public Project updateProject(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id, @RequestBody ProjectChange change) { return service.updateProject(actor(jwt, request), id, change); }
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProject(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id) { service.deleteProject(actor(jwt, request), id); }
    @GetMapping("/api-keys") public List<ApiKey> keys(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return service.keys(actor(jwt, request), null); }
    @GetMapping("/projects/{id}/api-keys") public List<ApiKey> projectKeys(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id) { return service.keys(actor(jwt, request), id); }
    @PostMapping("/projects/{id}/api-keys") @ResponseStatus(HttpStatus.CREATED) public CreatedKey createKey(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody KeyRequest body) { return service.createKey(actor(jwt, request), id, body.name(), body.environment(), body.expiresAt()); }
    @DeleteMapping("/projects/{id}/api-keys/{keyId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revokeKey(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID keyId) { service.revokeKey(actor(jwt, request), id, keyId); }
    @GetMapping("/team") public List<Member> members(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return service.members(actor(jwt, request)); }
    @PostMapping("/team/invite") @ResponseStatus(HttpStatus.CREATED) public Invitation invite(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @Valid @RequestBody InviteRequest body) { return service.invite(actor(jwt, request), body.email(), body.role()); }
    @PostMapping("/team/invitations/accept") public Member accept(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @Valid @RequestBody TokenRequest body) { return service.acceptInvitation(actor(jwt, request), body.token()); }
    @PatchMapping("/team/{id}/role") public Member changeRole(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody RoleRequest body) { return service.changeRole(actor(jwt, request), id, body.role()); }
    @DeleteMapping("/team/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeMember(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @PathVariable UUID id) { service.removeMember(actor(jwt, request), id); }
    @GetMapping("/org/security") public SecuritySettings security(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return service.security(actor(jwt, request)); }
    @PatchMapping("/org/security") public SecuritySettings updateSecurity(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @RequestBody SecurityChange change) { return service.updateSecurity(actor(jwt, request), change); }
    @GetMapping(value="/org/audit-log/export", produces="text/csv") public ResponseEntity<String> audit(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=sherlock-workspace-audit.csv").body(service.auditCsv(actor(jwt, request))); }
    @GetMapping("/org/subscription") public Subscription subscription(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) { return service.subscription(actor(jwt, request)); }
    @PostMapping("/org/subscription/upgrade") public Object upgrade(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, @Valid @RequestBody PlanRequest body) { return service.upgrade(actor(jwt, request), body.plan()); }
    public record KeyRequest(@NotBlank @Size(max=100) String name, @NotBlank String environment, Instant expiresAt) {}
    public record InviteRequest(@NotBlank @Size(max=254) String email, @NotBlank String role) {}
    public record RoleRequest(@NotBlank String role) {}
    public record TokenRequest(@NotBlank @Size(max=256) String token) {}
    public record PlanRequest(@NotBlank String plan) {}
}
