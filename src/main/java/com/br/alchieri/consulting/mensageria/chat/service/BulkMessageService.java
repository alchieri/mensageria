package com.br.alchieri.consulting.mensageria.chat.service;

import com.br.alchieri.consulting.mensageria.chat.dto.request.BulkMessageTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BulkMessageResponse;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

public interface BulkMessageService {

    /**
     * Inicia um job de envio em massa. Busca os contatos e enfileira as mensagens individuais.
     * @param request A requisição de envio em massa.
     * @param company A empresa que está fazendo a solicitação.
     * @return Uma resposta com o resumo do job iniciado.
     */
    BulkMessageResponse startBulkTemplateJob(BulkMessageTemplateRequest request, Company company, User user);
}
