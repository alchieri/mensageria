package com.br.alchieri.consulting.mensageria.config;

import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Alchieri Messaggistica")
                .version("v1")
                .description("API de Mensageria Multi-Channel"))
            .servers(List.of(
                new Server().url("https://mensageriaapi.alchiericonsulting.com").description("Servidor de Produção"),
                new Server().url("http://localhost:8082").description("Servidor Local")
            ))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Insira seu token JWT aqui."))
                .addSecuritySchemes("apiKeyAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-KEY")
                        .description("Para integrações via API Key."))
            ).addSecurityItem(new SecurityRequirement().addList("bearerAuth").addList("apiKeyAuth"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("1. Acesso Público & Login")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/webhook/**", "/api/v1/health/**")
                .build();
    }

    @Bean
    public GroupedOpenApi integrationApi() {
        return GroupedOpenApi.builder()
                .group("integracao")
                .displayName("2. API de Integração")
                .pathsToMatch(
                    "/api/v1/messages/**",
                    "/api/v1/templates/**",
                    "/api/v1/flow-data/**",
                    "/api/v1/media/**",
                    "/api/v1/auth/**",
                    "/api/v1/catalog/**" // Adicionei o catálogo aqui também
                )
                .addOpenApiCustomizer(openApi -> openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth").addList("apiKeyAuth")))
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("3. API Administrativa (Full)")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(openApi -> openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth")))
                .build();
    }
}
