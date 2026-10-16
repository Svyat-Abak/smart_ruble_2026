package com.smartruble.apigateway.filter;

import com.smartruble.apigateway.config.GatewayProperties;
import com.smartruble.apigateway.web.ErrorResponseWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Защита от перегрузки: ограничение частоты запросов алгоритмом token bucket.
 * <p>
 * У каждого клиента своё "ведро" на {@code capacity} жетонов. Каждый запрос забирает жетон,
 * жетоны пополняются со скоростью {@code refillPerSecond}. Пустое ведро — ответ 429.
 * Клиент определяется по id пользователя из токена, а без токена — по IP.
 * <p>
 * Счётчики хранятся в памяти, поэтому лимит считается отдельно на каждом экземпляре шлюза.
 * Если шлюзов станет несколько, используйте встроенный RequestRateLimiter + Redis (см. GUIDE.md).
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final long IDLE_BUCKET_TTL_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final GatewayProperties.RateLimit config;
    private final ErrorResponseWriter errorWriter;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(GatewayProperties properties, ErrorResponseWriter errorWriter) {
        this.config = properties.rateLimit();
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.enabled()) {
            return chain.filter(exchange);
        }
        return resolveKey(exchange).flatMap(key -> {
            TokenBucket bucket = buckets.computeIfAbsent(key,
                    k -> new TokenBucket(config.capacity(), config.refillPerSecond()));
            if (bucket.tryConsume()) {
                return chain.filter(exchange);
            }
            exchange.getResponse().getHeaders().set("Retry-After", "1");
            return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Слишком много запросов, повторите позже");
        });
    }

    private Mono<String> resolveKey(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(p -> "user:" + ((JwtAuthenticationToken) p).getToken().getSubject())
                .defaultIfEmpty("ip:" + clientIp(exchange));
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address != null && address.getAddress() != null ? address.getAddress().getHostAddress() : "unknown";
    }

    /** Удаляем вёдра клиентов, которые давно не приходили, чтобы не копить память. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void evictIdleBuckets() {
        long now = System.nanoTime();
        buckets.values().removeIf(b -> now - b.lastAccess() > IDLE_BUCKET_TTL_NANOS);
    }

    static final class TokenBucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefill;

        TokenBucket(int capacity, int refillPerSecond) {
            this.capacity = capacity;
            this.refillPerNano = refillPerSecond / 1_000_000_000.0;
            this.tokens = capacity;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized long lastAccess() {
            return lastRefill;
        }
    }

    @Override
    public int getOrder() {
        return FilterOrder.RATE_LIMIT;
    }
}
