package in.sherlock.gateway.interfaceadapter.web;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant started = Instant.now();
        return chain.filter(exchange).doFinally(signal -> log.info(
                "gateway_request method={} path={} status={} durationMs={} correlationId={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                exchange.getResponse().getStatusCode() == null
                        ? 0 : exchange.getResponse().getStatusCode().value(),
                Duration.between(started, Instant.now()).toMillis(),
                exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
