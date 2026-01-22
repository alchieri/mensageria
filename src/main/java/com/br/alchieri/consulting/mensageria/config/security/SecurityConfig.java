package com.br.alchieri.consulting.mensageria.config.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.br.alchieri.consulting.mensageria.config.filter.MdcFilter;
import com.br.alchieri.consulting.mensageria.model.enums.Role;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final UserDetailsService userDetailsService;
    private final MdcFilter mdcFilter;

    private static final String[] PUBLIC_WHITELIST = {
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/swagger-resources/**",
        "/webjars/**",
        "/actuator/**",
        "/",
        "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_WHITELIST).permitAll()

                // 1. Configuração base e grupo PÚBLICO devem ser acessíveis sem login
                // Isso permite carregar a tela inicial e o grupo "1. Acesso Público" onde o botão de login estará.
                .requestMatchers("/v3/api-docs/swagger-config", "/v3/api-docs/public").permitAll()

                // 2. Grupos restritos (Exigem Login/Token)
                .requestMatchers("/v3/api-docs/admin").hasAnyRole("BSP_ADMIN")
                .requestMatchers("/v3/api-docs/integracao").hasAnyRole("API_CLIENT", "COMPANY_ADMIN", "BSP_ADMIN", "COMPANY_USER")
                .requestMatchers("/v3/api-docs/internal").hasAnyRole("BSP_ADMIN")

                // 3. Bloqueia listagem geral raiz (opcional, segurança extra)
                .requestMatchers("/v3/api-docs").authenticated()
                // ... (restante das configurações)
                .requestMatchers("/api/v1/api-keys/**").hasAnyRole("ADMIN", "COMPANY_ADMIN", "BSP_ADMIN")

                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/register/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/webhook/whatsapp").permitAll() // Verificação do webhook
                .requestMatchers(HttpMethod.POST, "/api/v1/webhook/whatsapp").permitAll() // Notificações do webhook (protegido por assinatura HMAC)
                .requestMatchers("/api/v1/flow-data/**").permitAll()
                .requestMatchers("/api/v1/internal-callbacks/**").permitAll() // Permite, mas a lógica do controller protege com a chave
                
                .requestMatchers("/api/v1/admin/**").hasRole(Role.ROLE_BSP_ADMIN.name().replace("ROLE_", ""))
                .requestMatchers("/api/v1/company/**").hasAnyRole(Role.ROLE_COMPANY_ADMIN.name().replace("ROLE_", ""), Role.ROLE_BSP_ADMIN.name().replace("ROLE_", ""))

                .requestMatchers("/api/v1/messages/**").authenticated()
                .requestMatchers("/api/v1/templates/**").authenticated()
                .requestMatchers("/api/v1/media/**").authenticated()
                .requestMatchers("/api/v1/billing/**").authenticated()
                .requestMatchers("/api/v1/contacts/**").authenticated()
                .requestMatchers("/api/v1/campaigns/**").authenticated()
                .requestMatchers("/api/v1/chats/**").authenticated()
                .requestMatchers("/api/v1/flows/**").authenticated()
                .requestMatchers("/api/v1/health-check/**").authenticated()
                .requestMatchers("/api/v1/template-variables/**").authenticated()
                
                .anyRequest().authenticated()
            )
            // Configurar gerenciamento de sessão como STATELESS (não criar sessões HTTP)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Definir o provedor de autenticação
            .authenticationProvider(authenticationProvider())

            // MDC primeiro de tudo (Log Context)
            .addFilterBefore(mdcFilter, UsernamePasswordAuthenticationFilter.class)
            // API Key antes do JWT (Se tiver chave, usa. Se não, tenta token)
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // JWT antes do UsernamePassword (padrão)
            .addFilterAfter(jwtAuthFilter, ApiKeyAuthenticationFilter.class); 
            // Nota: .addFilterAfter garante que o JWT rode DEPOIS da tentativa de API Key

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "https://messaggistica.alchiericonsulting.com")); // Permite seu frontend
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*")); // Permite todos os headers
        configuration.setAllowCredentials(true); // Importante para JWT/cookies
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-API-KEY", "x-api-key")); // Opcional, se o frontend precisar ler headers da resposta
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService); // Nosso serviço que busca no DB
        authProvider.setPasswordEncoder(passwordEncoder()); // Encoder para verificar senha
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Necessário para o endpoint de login
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Usar BCrypt para hashing de senhas
        return new BCryptPasswordEncoder();
    }
}
