package in.sherlock.project.infrastructure.config;

import in.sherlock.project.application.ProjectService;
import in.sherlock.project.application.port.*;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class ProjectConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean ProjectService projectService(ProjectRepository repository, IdentityProvider identities,
            SecretProvider secrets, InvitationNotifier invitations, AccessPolicy policy, Clock clock,
            @Value("${sherlock.internal-token}") String internalToken) {
        if (internalToken.length() < 32) throw new IllegalStateException("SERVICE_INTERNAL_TOKEN must contain at least 32 characters");
        return new ProjectService(repository, identities, secrets, invitations, policy, clock);
    }
}
