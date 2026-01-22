package com.br.alchieri.consulting.mensageria.service.impl;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.callback.InternalCallbackPayload;
import com.br.alchieri.consulting.mensageria.service.InternalEventService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InternalEventServiceImpl implements InternalEventService {

    @Override
    public void processInternalEvent(InternalCallbackPayload<?> payload) {
        // Lógica de Negócio Centralizada
        log.info("EVENTO INTERNO PROCESSADO (Via Service Local). Evento ID: {}, Tipo: {}, Empresa ID: {}",
                 payload.getEventId(), payload.getEventType(), payload.getCompanyId());
        
        log.debug("Payload do evento interno: {}", payload.getData());

        // TODO: Aqui você implementará as integrações futuras:
        // 1. Salvar em log de auditoria no banco
        // 2. Publicar em fila Kafka/RabbitMQ para outros microsserviços
        // 3. Disparar webhooks para sistemas legados
    }
}
