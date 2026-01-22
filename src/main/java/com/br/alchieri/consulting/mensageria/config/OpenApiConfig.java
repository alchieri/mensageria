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
    servers = @Server(url = "/", description = "Default Server URL"),
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
        description = "Cole seu token JWT padrão aqui."
    ),
    @SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-KEY",
        description = "Para integrações M2M (Use o header X-API-KEY)."
    )
})
public class OpenApiConfig {

    /**
     * Grupo de Integração: Visível para clientes de API (Sistemas Terceiros)
     * Contém apenas endpoints operacionais.
     */
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

    /**
     * Grupo Administrativo: Visível apenas para BSP_ADMIN e COMPANY_ADMIN
     * Contém TODOS os endpoints da aplicação.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("API Administrativa (Full)")
                .pathsToMatch("/**")
                .build();
    }
}
