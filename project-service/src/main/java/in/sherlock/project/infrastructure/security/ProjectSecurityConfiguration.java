package in.sherlock.project.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ProjectSecurityConfiguration {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/info", "/internal/api-keys/validate").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    json.writeValue(response.getOutputStream(), java.util.Map.of("type", "about:blank", "title", "Unauthorized", "status", 401,
                            "code", "UNAUTHORIZED", "detail", "A valid access token is required."));
                }))
                .build();
    }
    @Bean JwtDecoder jwtDecoder(@Value("${sherlock.jwks-uri}") String jwksUri, @Value("${sherlock.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
    public static boolean matches(String expected, String supplied) {
        return expected != null && expected.length() >= 32 && supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
