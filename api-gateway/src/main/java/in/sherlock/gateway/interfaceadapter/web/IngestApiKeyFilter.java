package in.sherlock.gateway.interfaceadapter.web;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IngestApiKeyFilter implements GlobalFilter, Ordered {
    private final WebClient client;
    private final String internalToken;
    public IngestApiKeyFilter(WebClient.Builder builder, @Value("${PROJECT_SERVICE_URL:http://localhost:8082}") String projectUrl,
            @Value("${SERVICE_INTERNAL_TOKEN:}") String internalToken) {
        client = builder.baseUrl(projectUrl).build();
        this.internalToken = internalToken;
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/v1/ingest/")) return chain.filter(exchange);
        String key = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (key == null || key.length() > 256 || !(key.startsWith("demo_live_") || key.startsWith("demo_test_"))) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid SDK API key is required."));
        }
        if (internalToken.length() < 32) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SDK authentication is not configured."));
        return client.post().uri("/internal/api-keys/validate").header("X-Internal-Token", internalToken)
                .bodyValue(Map.of("apiKey", key)).retrieve().bodyToMono(KeyIdentity.class).timeout(Duration.ofSeconds(3))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Project service returned no identity.")))
                .onErrorMap(exception -> {
                    if (exception instanceof ResponseStatusException) return exception;
                    if (exception instanceof WebClientResponseException response && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 404)) {
                        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The API key is invalid, revoked, or expired.");
                    }
                    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SDK authentication is unavailable.");
                })
                .flatMap(identity -> {
                    if (identity.organizationId() == null || identity.projectId() == null || identity.environment() == null || identity.keyId() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Project service returned an invalid identity."));
                    }
                    exchange.getAttributes().put("sdkOrganizationId", identity.organizationId().toString());
                    ServerWebExchange authenticated = exchange.mutate().request(request -> request.headers(headers -> {
                        headers.remove("X-API-Key");
                        headers.remove("Authorization");
                        headers.remove("X-User-Id");
                        headers.remove("X-Role");
                        headers.set("X-Org-Id", identity.organizationId().toString());
                        headers.set("X-Project-Id", identity.projectId().toString());
                        headers.set("X-Environment", identity.environment());
                    })).build();
                    return chain.filter(authenticated);
                });
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 110; }
    public record KeyIdentity(UUID organizationId, UUID projectId, String environment, UUID keyId) {}
}
