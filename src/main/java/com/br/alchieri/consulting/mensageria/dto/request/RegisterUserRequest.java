package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description="Dados para registrar um novo usuário na plataforma.")
public class RegisterUserRequest {

    @NotBlank @Size(min = 3, max = 50)
    private String username;

    @NotBlank @Size(min = 8, max = 100)
    private String password;

    @NotBlank @Size(min = 3, max = 100)
    private String fullName;

    @NotBlank @Email @Size(max = 150)
    private String email;

    // O ID da empresa será fornecido pelo admin ou por um fluxo de criação de empresa
    // Para auto-registro, pode ser um campo opcional se a empresa for criada depois.
    @Schema(description = "ID da empresa à qual este usuário pertencerá (opcional no auto-registro, obrigatório se admin estiver criando).")
    private Long companyId;
}
