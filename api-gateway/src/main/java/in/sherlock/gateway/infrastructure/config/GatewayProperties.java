package in.sherlock.gateway.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sherlock.gateway")
public record GatewayProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri) {
}
