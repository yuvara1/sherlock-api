package in.sherlock.auth.infrastructure.config;

import in.sherlock.auth.application.port.out.AuthSettingsPort;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sherlock.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotBlank String jwtPrivateKeyBase64,
        @NotBlank String jwtPublicKeyBase64,
        @NotBlank String jwtKeyId,
        @NotBlank String encryptionKeyBase64,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotNull Duration mfaTtl,
        @NotNull Duration resetTtl,
        @NotEmpty List<String> allowedOrigins,
        String resetUrl,
        String mailFrom) implements AuthSettingsPort {
    public AuthProperties {
        if (accessTtl != null && (accessTtl.isNegative() || accessTtl.isZero())
                || refreshTtl != null && (refreshTtl.isNegative() || refreshTtl.isZero())
                || mfaTtl != null && (mfaTtl.isNegative() || mfaTtl.isZero())
                || resetTtl != null && (resetTtl.isNegative() || resetTtl.isZero())) {
            throw new IllegalArgumentException("Authentication token durations must be positive");
        }
        if (allowedOrigins != null) {
            allowedOrigins = allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        }
    }
}
