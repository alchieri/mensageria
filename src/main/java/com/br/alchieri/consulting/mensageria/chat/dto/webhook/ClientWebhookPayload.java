package com.br.alchieri.consulting.mensageria.chat.dto.webhook;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientWebhookPayload {
    private String action; // INIT, data_exchange, BACK
    private String currentScreen;
    private String userWaId; // WA_ID do usuário do WhatsApp
    private String flowToken;
    private JsonNode data; // Os dados do formulário
}
