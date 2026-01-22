package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Credenciais para autenticação do cliente.")
public class LoginRequest {

    @NotBlank
    @Schema(description = "Nome de usuário do cliente.", example = "testclient", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank
    @Schema(description = "Senha do cliente.", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
