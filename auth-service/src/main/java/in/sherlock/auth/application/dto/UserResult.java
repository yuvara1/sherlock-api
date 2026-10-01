package in.sherlock.auth.application.dto;

import in.sherlock.auth.domain.model.Role;
import in.sherlock.auth.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserResult(
        UUID id, String email, String name, Role role, UUID organizationId,
        boolean mfaEnabled, String avatarUrl, Instant createdAt) {
    public static UserResult from(UserAccount user) {
        return new UserResult(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.getOrganizationId(), user.isMfaEnabled(), user.getAvatarUrl(), user.getCreatedAt());
    }
}
