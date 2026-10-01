package in.sherlock.gateway.interfaceadapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sherlock.gateway.domain.exception.InvalidIdentityException;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayProblemHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper objectMapper;

    public GatewayProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (!(exception instanceof InvalidIdentityException) && !(exception instanceof ResponseStatusException)) {
            return Mono.error(exception);
        }
        HttpStatus status = exception instanceof ResponseStatusException response
                ? HttpStatus.valueOf(response.getStatusCode().value()) : HttpStatus.UNAUTHORIZED;
        String code = exception instanceof InvalidIdentityException ? "INVALID_TOKEN"
                : status == HttpStatus.UNAUTHORIZED ? "INVALID_API_KEY" : "SDK_AUTH_UNAVAILABLE";
        String detail = exception instanceof ResponseStatusException response ? response.getReason()
                : "The access token is missing required identity claims.";
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "type", "about:blank",
                    "title", status.getReasonPhrase(),
                    "status", status.value(),
                    "code", code,
                    "detail", detail == null ? status.getReasonPhrase() : detail));
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }
}
