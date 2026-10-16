package com.smartruble.apigateway.security;

import com.smartruble.apigateway.web.ErrorResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 401: токена нет, он просрочен или подделан. */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public JsonAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
        return writer.write(exchange, HttpStatus.UNAUTHORIZED, "Требуется валидный access-токен");
    }
}
