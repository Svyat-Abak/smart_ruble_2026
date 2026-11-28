package com.smartruble.billing.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(
        info = @Info(title = "SmartRuble Billing Service", version = "v1",
                description = "Контроль расходов: контрагенты, распоряжения и бюджеты, платежи, правила, согласование"),
        // относительный адрес: Swagger "Try it out" работает и напрямую, и через api-gateway
        servers = @Server(url = "/"),
        security = @SecurityRequirement(name = "bearerAuth"))
public class OpenApiConfig {
}
