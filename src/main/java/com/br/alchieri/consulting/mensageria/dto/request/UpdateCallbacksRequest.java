package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Dados para atualizar as URLs de callback de uma empresa.")
public class UpdateCallbacksRequest {

    @Pattern(regexp = "^https?://.+", message = "URL de callback geral inválida. Deve começar com http:// ou https://")
    @Schema(description = "Nova URL para callbacks gerais (status de mensagem, mensagens recebidas). Deixe nulo para não alterar.",
            example = "https://meucliente.com/api/whatsapp/events")
    private String callbackUrl;

    @Pattern(regexp = "^https?://.+", message = "URL de callback de status de template inválida. Deve começar com http:// ou https://")
    @Schema(description = "Nova URL para callbacks de status de template. Deixe nulo para não alterar.",
            example = "https://meucliente.com/api/whatsapp/template-updates")
    private String templateStatusCallbackUrl;

    // Adicionar outras URLs de callback específicas se você as tiver, por exemplo:
    // @Pattern(regexp = "^https?://.+", message = "URL de callback de dados de Flow inválida.")
    // @Schema(description = "Nova URL para receber dados de formulários de Flows.")
    // private String flowDataCallbackUrl;
}
