package com.br.alchieri.consulting.mensageria.chat.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ContactResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.CsvImportResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.MessageLogResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.service.ContactService;
import com.br.alchieri.consulting.mensageria.chat.service.MessageLogService;
import com.br.alchieri.consulting.mensageria.dto.request.ContactRequest;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/contacts")
@Tag(name = "Contact Management", description = "Endpoints para clientes gerenciarem seus contatos.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final MessageLogService messageLogService;

    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Listar Contatos da Empresa", description = "Retorna uma lista paginada dos contatos da empresa do usuário autenticado.")
    public ResponseEntity<Page<ContactResponse>> getCompanyContacts(@ParameterObject Pageable pageable) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Page<Contact> contactPage = contactService.getContactsByCompany(currentUser.getCompany(), pageable);
        return ResponseEntity.ok(contactPage.map(ContactResponse::fromEntity));
    }

    @PostMapping
    @Operation(summary = "Criar Novo Contato", description = "Cria um novo contato para a empresa do usuário autenticado.")
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody ContactRequest request) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Contact newContact = contactService.createContact(request, currentUser.getCompany());
        return ResponseEntity.status(HttpStatus.CREATED).body(ContactResponse.fromEntity(newContact));
    }

    @GetMapping("/{contactId}")
    @Operation(summary = "Obter Detalhes de um Contato", description = "Retorna os detalhes de um contato específico da empresa.")
    public ResponseEntity<ContactResponse> getContact(
            @Parameter(description = "ID do contato a ser buscado.", required = true) @PathVariable Long contactId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        return contactService.getContactByIdAndCompany(contactId, currentUser.getCompany())
                .map(contact -> ResponseEntity.ok(ContactResponse.fromEntity(contact)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{contactId}")
    @Operation(summary = "Atualizar um Contato", description = "Atualiza os dados de um contato existente.")
    public ResponseEntity<ContactResponse> updateContact(
            @Parameter(description = "ID do contato a ser atualizado.", required = true) @PathVariable Long contactId,
            @Valid @RequestBody ContactRequest request) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Contact updatedContact = contactService.updateContact(contactId, request, currentUser.getCompany());
        return ResponseEntity.ok(ContactResponse.fromEntity(updatedContact));
    }

    @DeleteMapping("/{contactId}")
    @Operation(summary = "Excluir um Contato", description = "Exclui um contato da base de dados da empresa.")
    public ResponseEntity<Void> deleteContact(
            @Parameter(description = "ID do contato a ser excluído.", required = true) @PathVariable Long contactId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        contactService.deleteContact(contactId, currentUser.getCompany());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar Contatos via CSV",
               description = "Faz upload de um arquivo CSV para criar ou atualizar contatos em lote. O CSV deve ter um cabeçalho com os nomes dos campos: 'name', 'phoneNumber', 'email', 'dateOfBirth', 'gender'.")
    public ResponseEntity<CsvImportResponse> uploadContacts(
            @Parameter(description = "Arquivo CSV com os contatos.", required = true,
                       schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        CsvImportResponse response = contactService.importContactsFromCsv(file, currentUser.getCompany());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contactId}/messages")
    @Operation(summary = "Obter Histórico de Mensagens de um Contato",
               description = "Retorna uma lista paginada e ordenada de todas as mensagens (enviadas e recebidas) para um contato específico.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Histórico de mensagens recuperado com sucesso."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido (contato não pertence à empresa).", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Contato não encontrado.", content = @Content)
    })
    public ResponseEntity<Page<MessageLogResponse>> getMessageHistoryForContact(
            @Parameter(description = "ID do contato cujo histórico será buscado.", required = true)
            @PathVariable Long contactId,
            @ParameterObject // Para o Swagger entender os parâmetros de paginação
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // @PageableDefault define valores padrão para paginação e ordenação

        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();

        // Primeiro, busca o contato para garantir que ele existe e pertence à empresa do usuário
        Contact contact = contactService.getContactByIdAndCompany(contactId, currentCompany)
                .orElseThrow(() -> new ResourceNotFoundException("Contato com ID " + contactId + " não encontrado ou não pertence à sua empresa."));

        // Agora, busca o histórico de mensagens para este contato
        Page<WhatsAppMessageLog> messageLogPage = messageLogService.getMessageHistoryForContact(contact, currentCompany, pageable);

        // Mapeia a página de entidades para uma página de DTOs de resposta
        Page<MessageLogResponse> responsePage = messageLogPage.map(MessageLogResponse::fromEntity);

        return ResponseEntity.ok(responsePage);
    }
}
