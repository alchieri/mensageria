package com.br.alchieri.consulting.mensageria.service;

import com.br.alchieri.consulting.mensageria.chat.dto.callback.InternalCallbackPayload;

public interface InternalEventService {
    void processInternalEvent(InternalCallbackPayload<?> payload);
}
