package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ActiveChatResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.WhatsAppMessageLogRepository;
import com.br.alchieri.consulting.mensageria.chat.service.MessageLogService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageLogServiceImpl implements MessageLogService {

    private final WhatsAppMessageLogRepository messageLogRepository;
    private final ContactRepository contactRepository;

    @Override
    public Page<WhatsAppMessageLog> getMessageHistoryForContact(Contact contact, Company company, Pageable pageable) {
        // Verificação de segurança: garantir que o contato realmente pertence à empresa solicitante
        if (!contact.getCompany().getId().equals(company.getId())) {
            log.warn("Tentativa de acesso não autorizado ao histórico do contato ID {} pela empresa ID {}.", contact.getId(), company.getId());
            throw new BusinessException("Contato não pertence à sua empresa.");
        }

        log.info("Buscando histórico de mensagens para o contato ID {} (Telefone: {}) da empresa ID {}",
                contact.getId(), contact.getPhoneNumber(), company.getId());

        return messageLogRepository.findByCompanyAndPhoneNumber(company, contact.getPhoneNumber(), pageable);
    }

    @Override
    public List<ActiveChatResponse> getActiveChats(Company company, int hours) {
        LocalDateTime sinceTimestamp = LocalDateTime.now().minusHours(hours);
        log.info("Buscando chats ativos para a empresa ID {} desde {}", company.getId(), sinceTimestamp);

        // 1. Encontrar os IDs das últimas mensagens de cada chat ativo
        List<Long> lastMessageIds = messageLogRepository.findActiveChatLastMessageIds(company, sinceTimestamp);

        if (lastMessageIds.isEmpty()) {
            return List.of(); // Retorna lista vazia
        }

        // 2. Buscar as entidades completas dessas últimas mensagens, ordenadas
        List<WhatsAppMessageLog> lastMessages = messageLogRepository.findByIdIn(
                lastMessageIds,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        // 3. Mapear para o DTO de resposta
        return lastMessages.stream()
                .map(log -> {
                    // Determina qual é o número do contato externo
                    String contactPhoneNumber = log.getDirection() == MessageDirection.INCOMING
                            ? log.getSender()
                            : log.getRecipient();

                    // Tenta encontrar o nome do contato no banco local
                    Optional<Contact> contactOpt = contactRepository.findByCompanyAndPhoneNumber(company, contactPhoneNumber);
                    String contactName = contactOpt.map(Contact::getName).orElse(null); // Pega o nome ou deixa nulo

                    return ActiveChatResponse.builder()
                            .contactPhoneNumber(contactPhoneNumber)
                            .contactName(contactName)
                            .lastMessageSnippet(truncate(log.getContent(), 50)) // Pega um trecho da msg
                            .lastMessageDirection(log.getDirection().name())
                            .lastMessageTimestamp(log.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveChatResponse> getChatList(Company company) {
        log.info("Buscando lista de chats para a empresa ID {}", company.getId());

        // 1. Busca a última mensagem de cada conversa
        List<WhatsAppMessageLog> lastMessages = messageLogRepository.findLastMessageOfEachChatForCompany(company.getId());

        // 2. Mapeia para o DTO, buscando informações adicionais
        return lastMessages.stream()
                .map(log -> {
                    String contactPhoneNumber = log.getDirection() == MessageDirection.INCOMING
                            ? log.getSender()
                            : log.getRecipient();

                    // Busca o contato para pegar o nome E a contagem de não lidas
                    Optional<Contact> contactOpt = contactRepository.findByCompanyAndPhoneNumber(company, contactPhoneNumber);
                    String contactName = contactOpt.map(Contact::getName).orElse(null);
                    long unreadCount = contactOpt.map(Contact::getUnreadMessagesCount).orElse(0); // Pega do contato

                    return ActiveChatResponse.builder()
                            .contactPhoneNumber(contactPhoneNumber)
                            .contactName(contactName)
                            .lastMessageSnippet(truncate(log.getContent(), 50))
                            .lastMessageDirection(log.getDirection().name())
                            .lastMessageTimestamp(log.getCreatedAt())
                            .unreadCount(unreadCount) // Usa o contador da entidade Contact
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true) // Mantém readOnly, a marcação de lido é outra operação
    public Page<WhatsAppMessageLog> getMessageHistory(String contactPhoneNumber, Company company, Pageable pageable) {
        log.info("Buscando histórico de mensagens para o contato {} da empresa ID {}", contactPhoneNumber, company.getId());
        return messageLogRepository.findByCompanyAndPhoneNumber(company, contactPhoneNumber, pageable);
    }

    @Override
    @Transactional
    public void markChatAsRead(String contactPhoneNumber, Company company) {
        log.info("Marcando mensagens do contato {} como lidas para a empresa ID {}", contactPhoneNumber, company.getId());
        
        // Encontra o contato e zera seu contador
        contactRepository.findByCompanyAndPhoneNumber(company, contactPhoneNumber)
                .ifPresent(contact -> {
                    if (contact.getUnreadMessagesCount() > 0) {
                        contact.setUnreadMessagesCount(0);
                        contactRepository.save(contact);
                        log.info("Contador de não lidas para o contato ID {} resetado para 0.", contact.getId());
                        // TODO: Opcional - Enviar evento WebSocket/SSE para o frontend para atualizar a UI em tempo real
                    }
                });
        // Não precisa mais do método no repository para update em lote
    }

    // Helper para truncar a mensagem
    private String truncate(String text, int length) {
        if (text == null || text.length() <= length) {
            return text;
        }
        return text.substring(0, length) + "...";
    }
}
