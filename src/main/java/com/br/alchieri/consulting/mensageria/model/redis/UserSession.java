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
    
    private boolean botActive;      // Se está preso num fluxo de bot
    private Long currentBotId;      // Qual bot está rodando
    private Long currentStepId;     // Em qual nó da árvore ele está

    private String currentState;    // Mantemos para compatibilidade ou uso híbrido
    
    // Dados temporários do contexto (Ex: nome digitado, opção escolhida)
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
