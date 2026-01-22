package com.br.alchieri.consulting.mensageria.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.LoginRequest;
import com.br.alchieri.consulting.mensageria.dto.response.LoginResponse;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.impl.JwtService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints para autenticação de clientes.")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    // O UserDetailsService já está configurado no AuthenticationProvider

    @Value("${jwt.expiration.ms}")
    private long jwtExpiration;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Autenticar Cliente", description = "Autentica um cliente e retorna um token JWT e informações do usuário para acesso aos endpoints protegidos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticação bem-sucedida",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (ex: campos faltando)",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = com.br.alchieri.consulting.mensageria.dto.response.ErrorResponse.class))), // Usando ErrorResponse se definido
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content)
    })
    public ResponseEntity<LoginResponse> authenticate(
                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Credenciais do cliente para login.",
                required = true,
                content = @Content(schema = @Schema(implementation = LoginRequest.class))
                ) @Valid @RequestBody LoginRequest request) {

        try {
        
                // Autentica usando o AuthenticationManager configurado
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

                // O principal é a nossa entidade Client, que implementa UserDetails
                User clientDetails = (User) authentication.getPrincipal();

                String jwtToken = jwtService.generateToken(clientDetails);

                // Usar o método helper para criar o LoginResponse
                LoginResponse response = LoginResponse.fromUserDetails(
                        clientDetails,
                        jwtToken,
                        jwtExpiration / 1000 // Converte para segundos
                );

                return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            // O GlobalExceptionHandler pode tratar isso ou você pode retornar um 401 específico.
            // Por enquanto, deixamos o Spring Security/GlobalExceptionHandler tratar.
            throw e;
        }
    }
}
