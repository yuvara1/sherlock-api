package in.sherlock.project.application.port;

import in.sherlock.project.domain.ProjectModels.*;

public interface AccessPolicy {
    void enforce(SecuritySettings settings, Actor actor, Identity identity);
    void validateCidrs(java.util.List<String> cidrs);
}
