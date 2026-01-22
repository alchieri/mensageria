package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.Set;

import com.br.alchieri.consulting.mensageria.model.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador de empresa atualizar um usuário existente em sua própria empresa.")
public class CompanyUpdateUserRequest {
    @Size(min = 3, max = 50)
    private String username;

    @Size(min = 8, max = 100)
    private String password; // Opcional, para resetar a senha

    @Size(min = 3, max = 100)
    private String fullName;

    @Email @Size(max = 150)
    private String email;

    private Boolean enabled;

    @Schema(description = "Novas roles para o usuário. Opções válidas: ROLE_USER, ROLE_COMPANY_ADMIN.")
    private Set<Role> roles;
}
