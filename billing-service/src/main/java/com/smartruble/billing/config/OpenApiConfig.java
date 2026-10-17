package com.smartruble.billing.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "SmartRuble Billing Service", version = "v1",
                description = "Лицевые счета, тарифы, начисления и платежи"),
        // относительный адрес: Swagger "Try it out" работает и напрямую, и через api-gateway
        servers = @Server(url = "/"))
public class OpenApiConfig {
}
