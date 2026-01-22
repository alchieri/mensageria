package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.Set;

import com.br.alchieri.consulting.mensageria.model.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador de empresa criar um novo usuário para sua própria empresa.")
public class CompanyCreateUserRequest {
    @NotBlank @Size(min = 3, max = 50)
    private String username;

    @NotBlank @Size(min = 8, max = 100)
    private String password;

    @NotBlank @Size(min = 3, max = 100)
    private String fullName;

    @NotBlank @Email @Size(max = 150)
    private String email;

    @Schema(description = "Roles a serem atribuídas ao novo usuário. Opções válidas para um Company Admin: ROLE_USER, ROLE_COMPANY_ADMIN.",
            example = "[\"ROLE_USER\"]")
    private Set<Role> roles; // Opcional, o serviço pode definir um padrão
}
