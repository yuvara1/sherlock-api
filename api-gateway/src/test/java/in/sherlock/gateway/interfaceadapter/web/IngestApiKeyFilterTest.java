package in.sherlock.gateway.interfaceadapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IngestApiKeyFilterTest {
    static final String TOKEN = "test-service-token-at-least-32-characters";

    @Test void missingKeyIsRejectedBeforeForwarding() {
        var filter = new IngestApiKeyFilter(WebClient.builder(), "http://localhost:8082", TOKEN);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/ingest/logs"));
        StepVerifier.create(filter.filter(exchange, forwarded -> Mono.error(new AssertionError("Must not forward"))))
                .expectErrorSatisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)).verify();
    }

    @Test void validatedKeyIsStrippedAndTenantIdentityIsForwarded() {
        var builder = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.headers().getFirst("X-Internal-Token")).isEqualTo(TOKEN);
            assertThat(request.url().getPath()).isEqualTo("/internal/api-keys/validate");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"organizationId":"00000000-0000-0000-0000-000000000001","projectId":"00000000-0000-0000-0000-000000000002","environment":"production","keyId":"00000000-0000-0000-0000-000000000003"}
                            """).build());
        });
        var filter = new IngestApiKeyFilter(builder, "http://localhost:8082", TOKEN);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/ingest/logs")
                .header("X-API-Key", "demo_live_random-secret").header("Authorization", "Bearer not-forwarded").header("X-User-Id", "spoofed").header("X-Role", "OWNER"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        StepVerifier.create(filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); })).verifyComplete();
        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-API-Key")).isNull();
        assertThat(headers.getFirst("Authorization")).isNull();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-Role")).isNull();
        assertThat(headers.getFirst("X-Project-Id")).isEqualTo("00000000-0000-0000-0000-000000000002");
        assertThat(headers.getFirst("X-Environment")).isEqualTo("production");
        assertThat(exchange.<String>getAttribute("sdkOrganizationId")).isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    @Test void upstreamValidationFailureIsNotAllowedThrough() {
        var builder = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build()));
        var filter = new IngestApiKeyFilter(builder, "http://localhost:8082", TOKEN);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/ingest/logs").header("X-API-Key", "demo_live_bad"));
        StepVerifier.create(filter.filter(exchange, forwarded -> Mono.error(new AssertionError("Must not forward"))))
                .expectErrorSatisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)).verify();
    }

    @Test void identityFilterDiscardsSpoofedServiceAndClientHeaders() {
        var filter = new GatewayIdentityFilter(new in.sherlock.gateway.application.service.ResolvePrincipalService(), TOKEN);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/projects")
                .remoteAddress(new java.net.InetSocketAddress("192.0.2.1", 443))
                .header("X-Internal-Token", "spoofed").header("X-Gateway-Token", "spoofed").header("X-Client-IP", "127.0.0.1")
                .header("X-Project-Id", "spoofed").header("X-Org-Id", "spoofed").header("X-User-Id", "spoofed"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        StepVerifier.create(filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); })).verifyComplete();
        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Internal-Token")).isNull();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-Org-Id")).isNull();
        assertThat(headers.getFirst("X-Project-Id")).isNull();
        assertThat(headers.getFirst("X-Gateway-Token")).isEqualTo(TOKEN);
        assertThat(headers.getFirst("X-Client-IP")).isEqualTo("192.0.2.1");
    }
}
