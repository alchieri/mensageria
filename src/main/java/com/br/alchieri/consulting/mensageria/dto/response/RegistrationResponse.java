package com.br.alchieri.consulting.mensageria.dto.response;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Resposta do registro bem-sucedido de uma nova empresa e usuário.")
public class RegistrationResponse {

    @Schema(description = "ID da nova empresa criada.")
    private Long companyId;

    @Schema(description = "Nome da nova empresa criada.")
    private String companyName;

    @Schema(description = "ID do novo usuário administrador criado.")
    private Long userId;

    @Schema(description = "Nome de usuário/email do novo administrador criado.")
    private String username;

    public static RegistrationResponse from(Company company, User user) {
        return RegistrationResponse.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }
}
