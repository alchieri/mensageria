package com.br.alchieri.consulting.mensageria.config.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.br.alchieri.consulting.mensageria.model.ApiKey;
import com.br.alchieri.consulting.mensageria.service.ApiKeyService;
import com.br.alchieri.consulting.mensageria.service.impl.AppUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Verifica se tem o header específico
        String apiKeyHeader = request.getHeader("X-API-KEY");

        if (apiKeyHeader != null && !apiKeyHeader.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 2. Valida no banco (e audita uso)
                Optional<ApiKey> validKey = apiKeyService.validateAndAudit(apiKeyHeader);

                if (validKey.isPresent()) {
                    ApiKey apiKey = validKey.get();
                    UserDetails userDetails = userDetailsService.loadUserByUsername(apiKey.getUser().getUsername());

                    // 3. Autentica no Spring Security
                    // Usamos null nas credenciais pois a API Key já provou autenticidade
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    log.debug("Usuário {} autenticado via API Key: {}", apiKey.getUser().getUsername(), apiKey.getName());
                }
            } catch (Exception e) {
                log.error("Erro ao autenticar via API Key", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
