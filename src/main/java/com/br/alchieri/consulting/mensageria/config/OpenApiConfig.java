package com.br.alchieri.consulting.mensageria.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Alchieri Messaggistica", version = "v1"),
    servers = {
        @Server(url = "https://mensageriaapi.alchiericonsulting.com", description = "Servidor de Produção"),
        @Server(url = "http://localhost:8082", description = "Servidor Local")
    },
    security = {
        @SecurityRequirement(name = "bearerAuth"),
        @SecurityRequirement(name = "apiKeyAuth")
    })
@SecuritySchemes({
    @SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Insira seu token JWT aqui."
    ),
    @SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-KEY",
        description = "Para integrações via API Key."
    )
})
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("1. Acesso Público & Login")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/webhook/**", "/api/v1/health/**", "/swagger-ui/**")
                .build();
    }

    @Bean
    public GroupedOpenApi integrationApi() {
        return GroupedOpenApi.builder()
                .group("integracao")
                .displayName("API de Integração")
                .pathsToMatch(
                    "/api/v1/messages/**", // Envio de mensagens
                    "/api/v1/templates/**",                 // Consulta de templates
                    "/api/v1/flow-data/**",                 // Consulta de respostas de Flow
                    "/api/v1/media/**",                     // Upload de mídia
                    "/api/v1/auth/**"                       // Login
                )
                // Opcional: Excluir endpoints administrativos que possam coincidir com os caminhos acima
                // .pathsToExclude("/api/v1/messages/admin/**") 
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("API Administrativa (Full)")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("API Completa")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
