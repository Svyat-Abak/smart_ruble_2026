package com.smartruble.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "SmartRuble Auth Service",
        version = "v1",
        description = "Регистрация, аутентификация и выдача JWT-токенов"),
        // относительный адрес: Swagger "Try it out" работает и напрямую, и через api-gateway
        servers = @Server(url = "/"))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";
}
