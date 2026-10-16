package com.smartruble.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Присваивает каждому запросу X-Request-Id (если клиент его не прислал), передаёт его в сервис
 * и возвращает клиенту. По этому id запрос можно найти в логах всех сервисов.
 * Заодно логирует: метод, путь, маршрут, статус, время.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";
    /** Атрибут с исходным путём запроса (нужен, например, в fallback-ответе). */
    public static final String ORIGINAL_PATH_ATTR = "smartruble.originalPath";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        final String id = requestId;
        long start = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(HEADER, id))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, id);
        exchange.getAttributes().put(ORIGINAL_PATH_ATTR, request.getPath().value());

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    log.info("{} {} -> {} [{}] {} ms (requestId={})",
                            request.getMethod(),
                            request.getPath().value(),
                            route != null ? route.getId() : "-",
                            exchange.getResponse().getStatusCode(),
                            (System.nanoTime() - start) / 1_000_000,
                            id);
                });
    }

    @Override
    public int getOrder() {
        return FilterOrder.REQUEST_ID;
    }
}
