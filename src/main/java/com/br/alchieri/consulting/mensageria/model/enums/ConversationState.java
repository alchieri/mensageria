package com.br.alchieri.consulting.mensageria.model.enums;

public enum ConversationState {
    IDLE,               // Estado inicial/parado
    WAITING_MENU_OPTION,// Esperando usuário escolher botão/lista
    WAITING_DOCUMENT,   // Esperando usuário digitar CPF/CNPJ
    WAITING_PROTOCOL,   // Esperando número de protocolo
    IN_SERVICE_HUMAN    // Em atendimento humano (Bot não responde)
}
