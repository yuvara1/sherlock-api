package in.sherlock.gateway.interfaceadapter.web;

import in.sherlock.gateway.application.port.in.ResolvePrincipalUseCase;
import in.sherlock.gateway.domain.exception.InvalidIdentityException;
import in.sherlock.gateway.domain.model.GatewayPrincipal;
import java.util.Optional;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayIdentityFilter implements GlobalFilter, Ordered {
    static final String USER_HEADER = "X-User-Id";
    static final String ORGANIZATION_HEADER = "X-Org-Id";
    static final String ROLE_HEADER = "X-Role";
    private static final Set<String> IDENTITY_HEADERS =
            Set.of(USER_HEADER, ORGANIZATION_HEADER, ROLE_HEADER, "X-Project-Id", "X-Environment", "X-Client-IP", "X-Gateway-Token", "X-Internal-Token");

    private final ResolvePrincipalUseCase principals;
    private final String internalToken;

    public GatewayIdentityFilter(ResolvePrincipalUseCase principals, @Value("${SERVICE_INTERNAL_TOKEN:}") String internalToken) {
        this.principals = principals;
        this.internalToken = internalToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = removeUntrustedIdentityHeaders(exchange);
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> authentication
                        .map(jwt -> forwardIdentity(sanitized, chain, jwt))
                        .orElseGet(() -> chain.filter(sanitized)));
    }

    private Mono<Void> forwardIdentity(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken authentication) {
        try {
            GatewayPrincipal principal = principals.resolve(authentication.getToken().getClaims());
            ServerWebExchange authenticated = exchange.mutate().request(request -> request.headers(headers -> {
                headers.set(USER_HEADER, principal.userId().toString());
                headers.set(ORGANIZATION_HEADER, principal.organizationId().toString());
                headers.set(ROLE_HEADER, principal.role());
            })).build();
            return chain.filter(authenticated);
        } catch (InvalidIdentityException exception) {
            return Mono.error(exception);
        }
    }

    private ServerWebExchange removeUntrustedIdentityHeaders(ServerWebExchange exchange) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            IDENTITY_HEADERS.forEach(headers::remove);
            if (internalToken.length() >= 32) {
                headers.set("X-Gateway-Token", internalToken);
                var remote = exchange.getRequest().getRemoteAddress();
                if (remote != null) headers.set("X-Client-IP", remote.getAddress().getHostAddress());
            }
        })).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
