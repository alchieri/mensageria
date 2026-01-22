package com.br.alchieri.consulting.mensageria.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Resposta da solicitação de envio em massa.")
public class BulkMessageResponse {

    @Schema(description = "ID único para rastrear este job de envio em massa (pode ser um UUID).")
    private String jobId;

    @Schema(description = "Status da solicitação (ex: ENFILEIRADO, LIMITE_EXCEDIDO).")
    private String status;

    @Schema(description = "Número estimado de contatos que serão processados.")
    private int estimatedContactCount;

    @Schema(description = "Mensagem informativa sobre a solicitação.")
    private String message;
}
