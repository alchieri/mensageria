package com.br.alchieri.consulting.mensageria.chat.model.enums;

public enum BotStepType {
    TEXT,       // Texto simples ou Menu (Lista/Botões)
    FLOW,       // Envia um Meta Flow
    TEMPLATE,   // Envia um Template HSM
    MEDIA,      // Envia uma mídia (imagem, áudio, vídeo, documento)
    HANDOFF,    // Encerra o bot e chama humano
    END         // Apenas encerra (Tchau)
}
