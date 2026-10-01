package in.sherlock.auth.infrastructure.config;

import in.sherlock.auth.domain.service.PasswordPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires framework-free domain services into the Spring context. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class ApplicationConfig {
    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
