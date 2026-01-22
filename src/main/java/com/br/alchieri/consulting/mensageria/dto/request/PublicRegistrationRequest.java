package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para registro público de uma nova empresa e seu primeiro usuário administrador.")
public class PublicRegistrationRequest {

    @NotNull @Valid
    @Schema(description = "Dados da empresa a ser criada.")
    private CompanyInfo company;

    @NotNull @Valid
    @Schema(description = "Dados do usuário administrador inicial da empresa.")
    private UserInfo user;

    @Data
    @Schema(name = "CompanyRegistrationInput")
    public static class CompanyInfo {
        @NotBlank @Size(max = 255)
        @Schema(description = "Nome da empresa cliente.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Minha Empresa de Teste")
        private String name;

        @Email @NotBlank @Size(max = 150)
        @Schema(description = "Email de contato principal da empresa.", requiredMode = Schema.RequiredMode.REQUIRED, example = "contato@minhaempresa.com")
        private String contactEmail;
    }

    @Data
    @Schema(name = "UserRegistrationInput")
    public static class UserInfo {
        @NotBlank @Size(min = 3, max = 100)
        @Schema(description = "Nome completo do usuário administrador da empresa.", requiredMode = Schema.RequiredMode.REQUIRED, example = "João da Silva")
        private String fullName;

        @NotBlank @Email @Size(max = 150)
        @Schema(description = "Email do usuário, será usado para login.", requiredMode = Schema.RequiredMode.REQUIRED, example = "joao.silva@minhaempresa.com")
        private String email;

        @NotBlank @Size(min = 8, max = 100)
        @Schema(description = "Senha de acesso do usuário.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Senha@Forte123")
        private String password;
    }
}
