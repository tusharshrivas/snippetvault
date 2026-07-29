package com.snippetvault.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SnippetVault API",
                version = "1.0.0",
                description = "A REST API for storing and retrieving code snippets. " +
                        "Authenticate using JWT Bearer tokens or the X-API-Key header."
        ),
        servers = {
                @Server(url = "/", description = "Current server")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste your access token from /api/auth/login or /api/auth/register"
)
public class OpenApiConfig {
    // Configuration is entirely annotation-driven — no bean methods needed.
    // Springdoc scans these annotations and builds the OpenAPI spec automatically.
}