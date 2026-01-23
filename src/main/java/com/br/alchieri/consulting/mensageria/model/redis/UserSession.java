package com.br.alchieri.consulting.mensageria.model.redis;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {

    private String phoneNumber;
    private Long companyId;
    
    // --- CONTROLE DE BOT ---
    private boolean botActive;      // Se está preso num fluxo de bot
    private Long currentBotId;      // Qual bot está rodando
    private Long currentStepId;     // Em qual nó da árvore ele está

    private String currentState;    // IDLE, IN_SERVICE_HUMAN, WAITING_...

    // --- NOVO: CONTROLE DE ATENDIMENTO HUMANO ---
    private Long assignedUserId;        // ID do Atendente (User.id) que "pegou" o chamado
    private String assignedUserName;    // Nome do atendente (para exibir rápido no front)
    private String assignedUserEmail;   // Email do atendente
    private String assignmentTime;      // Quando o atendimento começou (ISO String)
    
    // --- CONTEXTO ---
    @Builder.Default
    private Map<String, String> contextData = new HashMap<>();

    public void addContextData(String key, String value) {
        if (this.contextData == null) {
            this.contextData = new HashMap<>();
        }
        this.contextData.put(key, value);
    }
    
    public String getContextData(String key) {
        return this.contextData != null ? this.contextData.get(key) : null;
    }
}
