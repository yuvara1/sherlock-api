package in.sherlock.project.presentation;

import in.sherlock.project.application.ProjectService;
import in.sherlock.project.domain.ProjectException;
import in.sherlock.project.domain.ProjectModels.ValidatedKey;
import in.sherlock.project.infrastructure.security.ProjectSecurityConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalKeyController {
    private final ProjectService service;
    private final String token;
    public InternalKeyController(ProjectService service, @Value("${sherlock.internal-token}") String token) { this.service = service; this.token = token; }
    @PostMapping("/internal/api-keys/validate")
    @Transactional
    public ValidatedKey validate(@RequestHeader(value="X-Internal-Token", required=false) String supplied, @RequestBody KeyValidation body) {
        if (!ProjectSecurityConfiguration.matches(token, supplied)) throw new ProjectException(401, "UNAUTHORIZED", "Service authentication is required.");
        return service.validateKey(body.apiKey());
    }
    public record KeyValidation(String apiKey) {}
}
