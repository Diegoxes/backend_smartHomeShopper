package com.smarthome.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventario B2B API")
                        .description("""
                                API SaaS de inventario multi-organización (SmartInventory).

                                Autenticación: POST /auth/login o /auth/register → copiar el campo `token` \
                                → Authorize con esquema bearerAuth (JWT).

                                Endpoints públicos (sin token): /health, /auth/login, /auth/register, \
                                /auth/maintenance, /auth/password-reset, /webhook/**, documentación OpenAPI.""")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT devuelto en AuthResponse.token")));
    }
}
