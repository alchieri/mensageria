package com.br.alchieri.consulting.mensageria.dto.response;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

import com.br.alchieri.consulting.mensageria.model.User;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Resposta da autenticação contendo o token JWT e informações do usuário.")
public class LoginResponse {

     @Schema(description = "Token JWT para autenticação nas requisições subsequentes.")
    private String token;

    @Schema(description = "Tempo de expiração do token em segundos.")
    private long expiresIn;

    // Informações do usuário
    @Schema(description = "ID do usuário logado.")
    private Long userId;

    @Schema(description = "Nome de usuário (login).")
    private String username;

    @Schema(description = "Nome completo do usuário.")
    private String fullName;

    @Schema(description = "Email do usuário.")
    private String email;

    @Schema(description = "Indica se a conta do usuário está ativa.")
    private boolean enabled;

    @Schema(description = "Roles/Permissões do usuário.")
    private Set<String> roles;

    // Informações da Empresa (se o usuário pertencer a uma)
    @Schema(description = "ID da empresa à qual o usuário pertence (se aplicável).")
    private Long companyId;

    @Schema(description = "Nome da empresa à qual o usuário pertence (se aplicável).")
    private String companyName;

    public static LoginResponse fromUserDetails(User user, String token, long expiresInSeconds) {
        if (user == null) {
            return null;
        }
        Set<String> roleNames = user.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet());
        
        LoginResponseBuilder builder = LoginResponse.builder()
                .token(token)
                .expiresIn(expiresInSeconds)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .roles(roleNames);

        if (user.getCompany() != null) {
            builder.companyId(user.getCompany().getId());
            builder.companyName(user.getCompany().getName());
        }
        return builder.build();
    }
}
