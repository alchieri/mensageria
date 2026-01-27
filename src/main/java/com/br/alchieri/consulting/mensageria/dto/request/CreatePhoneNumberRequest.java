package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para registrar um novo número de telefone do WhatsApp (Channel) na empresa.")
public class CreatePhoneNumberRequest {

    @NotBlank(message = "O ID do telefone (Phone Number ID) é obrigatório.")
    @Schema(description = "ID do número de telefone fornecido pela Meta.", example = "100555888999")
    private String phoneNumberId;

    @NotBlank(message = "O WABA ID é obrigatório.")
    @Schema(description = "ID da conta do WhatsApp Business (WABA) à qual este número pertence.", example = "200333444555")
    private String wabaId;

    @Schema(description = "Número de telefone formatado para exibição.", example = "+55 45 99999-9999")
    private String displayPhoneNumber;

    @Schema(description = "Nome amigável para identificar este canal internamente (ex: Suporte, Vendas).", example = "Comercial Matriz")
    private String alias;

    @Schema(description = "Define se este será o número padrão para envios que não especificam remetente.", defaultValue = "false")
    private boolean isDefault = false;
}
