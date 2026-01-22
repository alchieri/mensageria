package com.br.alchieri.consulting.mensageria.chat.model.enums;

public enum FlowStatus {
    DRAFT,          // Rascunho, em edição
    PUBLISHED,      // Publicado e ativo
    DEPRECATED,     // Desativado pelo usuário, não pode mais ser enviado
    THROTTLED,      // Limitado pela Meta por problemas de saúde
    BLOCKED,        // Bloqueado pela Meta por problemas graves de saúde
    
    // Status internos (opcionais, mas úteis para nossa lógica)
    PUBLISH_FAILED, // Falha na última tentativa de publicação
    DISABLED        // Desativado por erros de validação
}
