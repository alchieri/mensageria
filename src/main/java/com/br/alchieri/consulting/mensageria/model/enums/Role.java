package com.br.alchieri.consulting.mensageria.model.enums;

public enum Role {
    ROLE_USER,          // Usuário padrão de uma empresa que pode enviar mensagens, gerenciar seus templates
    ROLE_COMPANY_ADMIN, // Admin de uma empresa (gerencia usuários da empresa, configs da empresa, templates da empresa)
    ROLE_BSP_ADMIN,     // Admin da plataforma BSP (gerencia empresas, planos globais, todos os dados)
    ROLE_API_CLIENT;    // Cliente de API (Usuário de Integração (Sistemas Terceiros))
}
