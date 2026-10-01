package in.sherlock.auth.application.dto;

import in.sherlock.auth.domain.model.Organization;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResult(UUID id, String name, String slug, String plan, Instant createdAt) {
    public static OrganizationResult from(Organization organization) {
        return new OrganizationResult(organization.getId(), organization.getName(),
                organization.getSlug(), organization.getPlan(), organization.getCreatedAt());
    }
}
