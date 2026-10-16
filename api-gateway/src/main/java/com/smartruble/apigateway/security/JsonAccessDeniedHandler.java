package com.smartruble.apigateway.security;

import com.smartruble.apigateway.web.ErrorResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 403: токен валиден, но нужной роли нет. */
@Component
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public JsonAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writer.write(exchange, HttpStatus.FORBIDDEN, "Недостаточно прав");
    }
}
