package com.br.alchieri.consulting.mensageria.dto.request;

import java.util.Set;

import com.br.alchieri.consulting.mensageria.model.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador BSP criar um novo usuário.")
public class AdminCreateUserRequest {

    @NotBlank @Size(min = 3, max = 50)
    @Schema(description = "Nome de usuário para login.", example = "novo.usuario", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank @Size(min = 8, max = 100)
    @Schema(description = "Senha do usuário.", example = "Senha@Forte123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank @Size(min = 2, max = 50)
    @Schema(description = "Primeiro nome do usuário.", example = "Jorge Valdir", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;

    @NotBlank @Size(min = 2, max = 100)
    @Schema(description = "Sobrenome do usuário.", example = "Alchieri", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;

    @NotBlank @Email @Size(max = 150)
    @Schema(description = "Email do usuário.", example = "jorge.alchieri@cliente.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "ID da empresa à qual este usuário pertencerá. Pode ser nulo se for um admin BSP sem empresa.")
    private Long companyId;

    @NotEmpty(message = "O usuário deve ter pelo menos uma role.")
    @Schema(description = "Roles a serem atribuídas ao usuário.",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"ROLE_USER\"]")
    private Set<Role> roles;
}
