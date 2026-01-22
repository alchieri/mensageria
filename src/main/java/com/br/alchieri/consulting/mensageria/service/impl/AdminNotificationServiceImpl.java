package com.br.alchieri.consulting.mensageria.service.impl;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.service.AdminNotificationService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminNotificationServiceImpl implements AdminNotificationService {

    @Override
    public void notifyCallbackFailure(String subject, String details) {
        // Implementação real enviaria um email, mensagem no Slack, etc.
        // Por enquanto, logamos como um ERRO crítico que pode ser pego por sistemas de monitoramento.
        log.error("ALERTA DE FALHA CRÍTICA DE CALLBACK | Assunto: {} | Detalhes: {}", subject, details);
        // TODO: Integrar com AWS SES, SNS, Slack API, etc.
    }
}
