package in.sherlock.auth.presentation.rest;

import in.sherlock.auth.application.port.in.WorkspaceIdentityUseCase;
import in.sherlock.auth.domain.exception.AuthException;
import in.sherlock.auth.domain.model.Role;
import in.sherlock.auth.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkspaceIdentityController {
    private final WorkspaceIdentityUseCase identities;
    private final String internalToken;
    public WorkspaceIdentityController(WorkspaceIdentityUseCase identities, @Value("${SERVICE_INTERNAL_TOKEN:}") String internalToken) {
        this.identities = identities; this.internalToken = internalToken;
    }
    @GetMapping("/api/v1/auth/workspace")
    public WorkspaceIdentityUseCase.WorkspaceIdentity workspace(@AuthenticationPrincipal AuthPrincipal principal) {
        return identities.workspace(principal.userId(), principal.sessionId());
    }
    @PostMapping("/internal/project/users/{userId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void membership(@RequestHeader(value="X-Internal-Token", required=false) String supplied,
            @PathVariable UUID userId, @Valid @RequestBody MembershipRequest body) {
        if (internalToken.length() < 32 || supplied == null || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException(401, "UNAUTHORIZED", "Service authentication is required.");
        }
        identities.membership(userId, body.organizationId(), body.role(), body.revoked());
    }
    public record MembershipRequest(@NotNull UUID organizationId, @NotNull Role role, boolean revoked) {}
}
