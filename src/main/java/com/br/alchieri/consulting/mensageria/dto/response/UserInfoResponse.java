package com.br.alchieri.consulting.mensageria.dto.response;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;
import com.br.alchieri.consulting.mensageria.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações detalhadas e públicas de um usuário da plataforma.")
public class UserInfoResponse {

    @Schema(description = "ID único do usuário.")
    private Long id;

    @Schema(description = "Nome de usuário (login).")
    private String username;

    @Schema(description = "Nome completo do usuário.")
    private String fullName;

    @Schema(description = "Email de contato do usuário.")
    private String email;

    @Schema(description = "Indica se a conta do usuário está ativa.")
    private boolean enabled;

    @Schema(description = "Roles/Permissões atribuídas ao usuário.")
    private Set<String> roles;

    // Informações da Empresa associada
    @Schema(description = "ID da empresa à qual o usuário pertence (se aplicável).")
    private Long companyId;

    @Schema(description = "Nome da empresa à qual o usuário pertence (se aplicável).")
    private String companyName;

    // Campos da Meta que podem ser úteis para o frontend
    @Schema(description = "ID do Número de Telefone primário da empresa associada.")
    private String metaPrimaryPhoneNumberId;

    @Schema(description = "ID da WABA da empresa associada.")
    private String metaWabaId;

    @Schema(description = "Status do onboarding da empresa associada.")
    private OnboardingStatus onboardingStatus;

    /**
     * Método de fábrica para converter uma entidade User em um DTO de resposta.
     * @param user A entidade User a ser convertida.
     * @return um UserInfoResponse preenchido.
     */
    public static UserInfoResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }

        UserInfoResponseBuilder builder = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .roles(user.getAuthorities().stream()
                             .map(GrantedAuthority::getAuthority)
                             .collect(Collectors.toSet()));

        if (user.getCompany() != null) {
            builder.companyId(user.getCompany().getId());
            builder.companyName(user.getCompany().getName());
            builder.metaPrimaryPhoneNumberId(user.getCompany().getMetaPrimaryPhoneNumberId());
            builder.metaWabaId(user.getCompany().getMetaWabaId());
            builder.onboardingStatus(user.getCompany().getOnboardingStatus());
        }

        return builder.build();
    }
}
