package in.sherlock.gateway.infrastructure.config;

import in.sherlock.gateway.application.port.in.ResolvePrincipalUseCase;
import in.sherlock.gateway.application.service.ResolvePrincipalService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayBeans {
    @Bean
    ResolvePrincipalUseCase resolvePrincipalUseCase() {
        return new ResolvePrincipalService();
    }

    @Bean
    KeyResolver organizationKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.<String>getAttribute("sdkOrganizationId"))
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> authentication.getToken().getClaimAsString("orgId"))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var address = exchange.getRequest().getRemoteAddress();
                    return address == null ? "anonymous" : address.getAddress().getHostAddress();
                })));
    }
}
