package in.sherlock.gateway.interfaceadapter.web;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = valid(supplied) ? supplied : UUID.randomUUID().toString();
        ServerWebExchange correlated = exchange.mutate().request(request ->
                request.headers(headers -> headers.set(HEADER, correlationId))).build();
        correlated.getResponse().beforeCommit(() -> {
            correlated.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });
        return chain.filter(correlated);
    }

    private static boolean valid(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 128
                && value.chars().allMatch(character -> character >= 33 && character <= 126);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
