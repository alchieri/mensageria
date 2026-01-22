package com.br.alchieri.consulting.mensageria.chat.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ActiveChatResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface MessageLogService {

    /**
     * Obtém o histórico de mensagens de um contato específico, de forma paginada.
     * @param contact O contato cujo histórico será buscado.
     * @param company A empresa dona do contato (para verificação de segurança).
     * @param pageable Configuração de paginação e ordenação.
     * @return Uma página de logs de mensagem.
     */
    Page<WhatsAppMessageLog> getMessageHistoryForContact(Contact contact, Company company, Pageable pageable);

    List<ActiveChatResponse> getActiveChats(Company company, int hours);

    List<ActiveChatResponse> getChatList(Company company);
    Page<WhatsAppMessageLog> getMessageHistory(String contactPhoneNumber, Company company, Pageable pageable);
    void markChatAsRead(String contactPhoneNumber, Company company);
}
