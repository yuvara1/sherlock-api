package in.sherlock.project.infrastructure.security;

import in.sherlock.project.application.port.AccessPolicy;
import in.sherlock.project.domain.ProjectException;
import in.sherlock.project.domain.ProjectModels.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccessPolicy implements AccessPolicy {
    private final Clock clock;
    public WorkspaceAccessPolicy(Clock clock) { this.clock = clock; }
    @Override public void enforce(SecuritySettings settings, Actor actor, Identity identity) {
        if (settings.mfaEnforced() && !identity.mfaEnabled()) throw new ProjectException(403, "MFA_REQUIRED", "Enable MFA before accessing this workspace.");
        if (!settings.ipAllowlist().isEmpty() && settings.ipAllowlist().stream().noneMatch(cidr -> new IpAddressMatcher(cidr).matches(actor.ip()))) {
            throw new ProjectException(403, "IP_NOT_ALLOWED", "This IP address is not allowed.");
        }
        Duration timeout = switch (settings.sessionTimeout()) {
            case "H1" -> Duration.ofHours(1);
            case "H8" -> Duration.ofHours(8);
            case "D7" -> Duration.ofDays(7);
            default -> Duration.ofHours(24);
        };
        if (identity.sessionStartedAt() == null || !identity.sessionStartedAt().plus(timeout).isAfter(clock.instant())) {
            throw new ProjectException(401, "SESSION_EXPIRED", "The workspace session has expired.");
        }
    }
    @Override public void validateCidrs(List<String> cidrs) {
        if (cidrs.size() > 100) throw ProjectException.invalid("At most 100 CIDR ranges are supported.");
        for (String cidr : cidrs) {
            try {
                if (cidr == null || cidr.length() > 64 || !cidr.matches("[0-9a-fA-F:.]+(/[0-9]{1,3})?")) throw new IllegalArgumentException();
                new IpAddressMatcher(cidr);
            } catch (IllegalArgumentException exception) { throw ProjectException.invalid("Invalid CIDR range."); }
        }
    }
}
