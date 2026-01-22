package com.br.alchieri.consulting.mensageria.chat.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ActiveChatResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.MessageLogResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.service.MessageLogService;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chats")
@Tag(name = "Chat & Message History", description = "Endpoints para visualização de chats ativos e históricos de mensagens.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ChatController {

    private final MessageLogService messageLogService;
    private final ContactRepository contactRepository; // Para buscar o contato pelo número
    private final SecurityUtils securityUtils;

    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Chats Ativos",
               description = "Retorna uma lista de conversas que tiveram uma mensagem recebida (do cliente para a empresa) nas últimas 24 horas, ordenadas pela última mensagem.")
    public ResponseEntity<List<ActiveChatResponse>> getActiveChats(
            @Parameter(description = "Período em horas para considerar um chat como ativo (baseado na última mensagem recebida).", example = "24")
            @RequestParam(defaultValue = "24") int sinceHours
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany(); // getCompanyOfUser do seu serviço

        List<ActiveChatResponse> activeChats = messageLogService.getActiveChats(currentCompany, sinceHours);
        return ResponseEntity.ok(activeChats);
    }

    @GetMapping(value = "/{phoneNumber}/history-old", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obter Histórico de Mensagens com um Contato",
               description = "Retorna o histórico completo e paginado de mensagens trocadas com um número de telefone específico.")
    public ResponseEntity<Page<MessageLogResponse>> getMessageHistoryForPhoneNumber(
            @Parameter(description = "Número de telefone do contato no formato E.164 (ex: 5511999998888).", required = true)
            @PathVariable String phoneNumber,
            @ParameterObject
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();

        // Encontrar o contato pelo número para passar ao serviço.
        // Isso também serve como uma forma de garantir que estamos lidando com um contato conhecido,
        // embora o serviço de log possa funcionar apenas com o número.
        Contact contact = contactRepository.findByCompanyAndPhoneNumber(currentCompany, phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum contato encontrado com o número " + phoneNumber + " para sua empresa."));

        Page<WhatsAppMessageLog> messageLogPage = messageLogService.getMessageHistoryForContact(contact, currentCompany, pageable);
        return ResponseEntity.ok(messageLogPage.map(MessageLogResponse::fromEntity));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Conversas",
               description = "Retorna uma lista de todas as conversas da empresa, ordenadas pela última mensagem. Inclui a contagem de mensagens não lidas.")
    public ResponseEntity<List<ActiveChatResponse>> getChatList() {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        List<ActiveChatResponse> activeChats = messageLogService.getChatList(currentCompany); // <<< MUDOU AQUI
        return ResponseEntity.ok(activeChats);
    }

    @GetMapping(value = "/{phoneNumber}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obter Histórico de Mensagens de uma Conversa",
               description = "Retorna o histórico completo e paginado de mensagens trocadas com um número de telefone específico.")
    public ResponseEntity<Page<MessageLogResponse>> getMessageHistory(
            @Parameter(description = "Número de telefone do contato no formato E.164.", required = true)
            @PathVariable String phoneNumber,
            @ParameterObject
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        Page<WhatsAppMessageLog> messageLogPage = messageLogService.getMessageHistory(phoneNumber, currentCompany, pageable); // <<< MUDOU AQUI
        return ResponseEntity.ok(messageLogPage.map(MessageLogResponse::fromEntity));
    }

    @PostMapping(value = "/{phoneNumber}/mark-as-read")
    @Operation(summary = "Marcar Conversa como Lida",
               description = "Zera o contador de mensagens não lidas para uma conversa específica.")
    public ResponseEntity<Void> markChatAsRead(
            @Parameter(description = "Número de telefone do contato cuja conversa será marcada como lida.", required = true)
            @PathVariable String phoneNumber) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        messageLogService.markChatAsRead(phoneNumber, currentCompany); // <<< MUDOU AQUI
        return ResponseEntity.noContent().build();
    }
}
