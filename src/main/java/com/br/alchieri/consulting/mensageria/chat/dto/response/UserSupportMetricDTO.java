package com.br.alchieri.consulting.mensageria.chat.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSupportMetricDTO {

    private Long userId;
    private String userName;
    private String userEmail;
    
    // Métricas
    private long messagesSent;              // Total de mensagens enviadas
    private long distinctContactsHandled;   // Quantos clientes diferentes foram atendidos (responderam)
    private long chatsViewed;               // Quantas conversas foram abertas/lidas (Recibo Interno)
    
    // Cálculo derivado simples (exemplo)
    public double getAvgMessagesPerContact() {
        return distinctContactsHandled == 0 ? 0 : (double) messagesSent / distinctContactsHandled;
    }
}
