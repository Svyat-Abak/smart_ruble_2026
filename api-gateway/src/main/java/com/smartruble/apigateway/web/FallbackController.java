package com.smartruble.apigateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Сюда circuit breaker перенаправляет запрос, если сервис недоступен,
 * не ответил вовремя или "цепь разомкнута" после серии ошибок.
 */
@RestController
public class FallbackController {

    private final ErrorResponseWriter writer;

    public FallbackController(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @RequestMapping("/fallback/{service}")
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                "Сервис " + service + " временно недоступен, попробуйте позже");
    }
}
