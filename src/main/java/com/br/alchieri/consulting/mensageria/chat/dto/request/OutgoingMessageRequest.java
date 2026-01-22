package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutgoingMessageRequest implements Serializable { 
    
    private static final long serialVersionUID = 1L;

    private String messageType; // "TEXT", "TEMPLATE", "INTERACTIVE_FLOW"
    private Long userId; // ID do usuário que solicitou (para buscar configs/limites)
    private String originalRequestId; // Opcional: Para rastreamento (ex: MDC traceId)

    // Incluir os DTOs originais ou campos específicos
    // Marcar como transient se não quiser serializar pelo Jackson padrão, mas ok para JSON
    private SendTextMessageRequest textRequest;
    private SendTemplateMessageRequest templateRequest;
    private SendInteractiveFlowMessageRequest interactiveFlowRequest;

    // Adicionar outros tipos se necessário
    private Long scheduledMessageId;
}
