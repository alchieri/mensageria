package com.br.alchieri.consulting.mensageria.dto.request;

import java.util.Set;

import com.br.alchieri.consulting.mensageria.model.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador atualizar um usuário existente.")
public class AdminUpdateUserRequest {

    @Size(min = 3, max = 50)
    private String username;

    @Size(min = 8, max = 100)
    private String password; // Opcional, para alterar senha

    @Size(min = 3, max = 100)
    private String fullName;

    @Email @Size(max = 150)
    private String email;

    private Boolean enabled;

    @Schema(description = "ID da empresa à qual o usuário pertence (se for alterar).")
    private Long companyId;

    @Schema(description = "Novas roles para o usuário.")
    private Set<Role> roles;
}
