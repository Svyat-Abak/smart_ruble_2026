package com.smartruble.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Передаёт данные о пользователе во внутренние сервисы заголовками:
 * <ul>
 *     <li>X-User-Id    — id пользователя (UUID из claim "sub")</li>
 *     <li>X-User-Phone — телефон</li>
 *     <li>X-User-Roles — роли через запятую</li>
 * </ul>
 * Сначала ВСЕГДА удаляем такие заголовки из входящего запроса — иначе клиент мог бы
 * прислать "X-User-Roles: ROLE_ADMIN" сам. Заголовок Authorization оставляем:
 * сервисы могут перепроверить токен сами.
 */
@Component
public class UserHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_PHONE = "X-User-Phone";
    public static final String USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest cleaned = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_PHONE);
                    h.remove(USER_ROLES);
                })
                .build();
        ServerWebExchange cleanedExchange = exchange.mutate().request(cleaned).build();

        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> withUserHeaders(cleanedExchange, auth))
                .defaultIfEmpty(cleanedExchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, JwtAuthenticationToken auth) {
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String phone = auth.getToken().getClaimAsString("phone");

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.set(USER_ID, auth.getToken().getSubject());
                    h.set(USER_ROLES, roles);
                    if (phone != null) {
                        h.set(USER_PHONE, phone);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return FilterOrder.USER_HEADERS;
    }
}
