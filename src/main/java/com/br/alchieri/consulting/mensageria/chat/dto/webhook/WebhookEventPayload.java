package com.br.alchieri.consulting.mensageria.chat.dto.webhook;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WebhookEventPayload implements Serializable {
    private String rawPayload; // O JSON bruto vindo da Meta
    private String signature; // A assinatura do header
    private LocalDateTime receivedTimestamp; // Quando o webhook foi recebido pela nossa API
}
