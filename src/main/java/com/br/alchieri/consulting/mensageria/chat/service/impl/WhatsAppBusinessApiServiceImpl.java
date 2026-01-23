package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.meta.WhatsAppBusinessApiCreateTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplateInfoResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplatePushResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplateSyncResponse;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.chat.repository.ClientTemplateRepository;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppBusinessApiService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WhatsAppBusinessApiServiceImpl implements WhatsAppBusinessApiService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppBusinessApiServiceImpl.class);

    private final WebClient.Builder webClientBuilder;
    private final CompanyRepository companyRepository;
    private final ClientTemplateRepository clientTemplateRepository;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    // URL base global
    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}") // <<< SEU SYSTEM USER TOKEN GLOBAL DE BSP
    private String bspSystemUserAccessToken;

    @SuppressWarnings("null")
    @Override
    @Transactional // Garante que salvar o ClientTemplate e a chamada à Meta sejam atômicos ou rollback
    public Mono<ClientTemplate> createTemplate(CreateTemplateRequest request, User creator) {
        
        if (creator == null) {
            return Mono.error(new BusinessException("Usuário criador não fornecido."));
        }

        Company currentCompany = getCompanyFromUser(creator);

        if (!billingService.canCompanyCreateTemplate(currentCompany)) {
            logger.warn("Empresa ID {}: Limite de templates ativos excedido.", currentCompany.getId());
            return Mono.error(new BusinessException("Limite de templates ativos excedido."));
        }

        String clientWabaId = getCompanyWabaId(currentCompany);
        WebClient bspWebClient = getBspWebClient();
        String endpoint = "/" + clientWabaId + "/message_templates";
        logger.info("Empresa ID {}: Criando template '{}' na WABA {}.", currentCompany.getId(), request.getName(), clientWabaId);

        ClientTemplate clientTemplate = clientTemplateRepository
            .findByCompanyAndTemplateNameAndLanguage(currentCompany, request.getName(), request.getLanguage())
            .orElseGet(() -> {
                ClientTemplate newCt = new ClientTemplate();
                newCt.setCompany(currentCompany);
                newCt.setTemplateName(request.getName());
                newCt.setLanguage(request.getLanguage());
                return newCt;
            });

        clientTemplate.setCategory(request.getCategory().toUpperCase());
        try {
            clientTemplate.setComponentsJson(objectMapper.writeValueAsString(request.getComponents()));
        } catch (JsonProcessingException e) {
            logger.error("BSP: Company ID {}: Erro ao serializar componentes do template {}: {}", currentCompany.getId(), request.getName(), e.getMessage());
            clientTemplate.setStatus("INVALID_DATA_SERIALIZATION");
            clientTemplate.setReason("Falha ao serializar componentes: " + e.getMessage());
            clientTemplateRepository.save(clientTemplate); // Salva o erro
            return Mono.error(new BusinessException("Erro ao processar componentes do template.", e));
        }
        clientTemplate.setStatus("SUBMITTING");
        final ClientTemplate submittingClientTemplate = clientTemplateRepository.save(clientTemplate);

        try {
            WhatsAppBusinessApiCreateTemplateRequest metaRequest = mapToMetaCreateTemplateRequest(request);

            return bspWebClient.post().uri(endpoint).body(BodyInserters.fromValue(metaRequest)).retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        logger.error("BSP: Company ID {}: Erro API Meta ao criar template: Status={}, Body={}", currentCompany.getId(), clientResponse.statusCode(), errorBody);
                                        submittingClientTemplate.setStatus("META_API_ERROR");
                                        submittingClientTemplate.setReason(String.format("Meta API Error (%s): %s", clientResponse.statusCode(), errorBody));
                                        clientTemplateRepository.save(submittingClientTemplate);
                                        return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), "Erro API Meta", null, errorBody.getBytes(), null));
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .map(responseNode -> {
                        String metaTemplateId = responseNode.path("id").asText(null);
                        String metaStatus = responseNode.path("status").asText("PENDING_APPROVAL");
                        submittingClientTemplate.setMetaTemplateId(metaTemplateId);
                        submittingClientTemplate.setStatus(metaStatus.toUpperCase());
                        submittingClientTemplate.setReason(null);
                        ClientTemplate savedTemplate = clientTemplateRepository.save(submittingClientTemplate);
                        logger.info("BSP: Company ID {}: Template '{}' submetido. ClientTemplate ID: {}, Meta ID: {}, Meta Status: {}",
                                currentCompany.getId(), request.getName(), submittingClientTemplate.getId(), metaTemplateId, metaStatus);
                        return savedTemplate;
                    });
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            logger.error("BSP: Company ID {}: Erro mapeamento template {}: {}", currentCompany.getId(), request.getName(), e.getMessage());
            submittingClientTemplate.setStatus("INVALID_DATA");
            submittingClientTemplate.setReason(e.getMessage());
            clientTemplateRepository.save(submittingClientTemplate);
            return Mono.error(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientTemplate> listTemplatesForUser(User user, Optional<Long> companyId, Pageable pageable) {
        if (user.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            if (companyId.isPresent()) {
                logger.info("Admin ID {}: Listando templates para a empresa ID {}", user.getId(), companyId.get());
                Company company = companyRepository.findById(companyId.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId.get() + " não encontrada."));
                return clientTemplateRepository.findByCompany(company, pageable);
            } else {
                logger.info("Admin ID {}: Listando todos os templates do sistema.", user.getId());
                return clientTemplateRepository.findAllWithCompany(pageable);
            }
        } else {
            Company userCompany = getCompanyFromUser(user);
            if (companyId.isPresent() && !companyId.get().equals(userCompany.getId())) {
                throw new AccessDeniedException("Você não tem permissão para visualizar templates de outra empresa.");
            }
            logger.info("Empresa ID {}: Listando templates do banco de dados.", userCompany.getId());
            return clientTemplateRepository.findByCompany(userCompany, pageable);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<ClientTemplate> getTemplateDetails(String templateName, String language, User user) {
        Company company = getCompanyFromUser(user);

        return Mono.fromCallable(() -> clientTemplateRepository
                .findByCompanyAndTemplateNameAndLanguage(company, templateName, language)
                .orElseThrow(() -> new ResourceNotFoundException("Template '" + templateName + "' (" + language + ") não encontrado no seu catálogo.")));
    }

    @Override
    @Transactional
    public Mono<ApiResponse> deleteTemplate(String templateName, String language, User user) {
        
        Company company = getCompanyFromUser(user);
        
        ClientTemplate clientTemplate = clientTemplateRepository
                .findByCompanyAndTemplateNameAndLanguage(company, templateName, language)
                .orElseThrow(() -> new ResourceNotFoundException("Template '" + templateName + "' (" + language + ") não encontrado no seu catálogo."));

        WebClient bspWebClient = getBspWebClient();
        String clientWabaId = getCompanyWabaId(company);
        String endpoint = "/" + clientWabaId + "/message_templates";
        logger.info("Empresa ID {}: Deletando template '{}' ({}) da WABA {}",
                company.getId(), templateName, language, clientWabaId);

        return bspWebClient.method(HttpMethod.DELETE)
                 .uri(uriBuilder -> uriBuilder.path(endpoint).queryParam("name", templateName).build())
                 .retrieve()
                 .bodyToMono(JsonNode.class) // Tenta obter o corpo de sucesso
                 .flatMap(responseNode -> { // Fluxo de sucesso
                    boolean success = responseNode.path("success").asBoolean(false);
                    if (success) {
                        logger.info("Template '{}' deletado com sucesso na API da Meta. Removendo do banco local.", templateName);
                        clientTemplateRepository.delete(clientTemplate);
                        return Mono.just(new ApiResponse(true, "Template deletado com sucesso.", null));
                    } else {
                        logger.warn("API Meta indicou falha ao deletar template '{}', resposta: {}", templateName, responseNode);
                        return Mono.error(new BusinessException("Falha indicada pela API ao deletar template."));
                    }
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    // --- LÓGICA DE TRATAMENTO DE ERRO AQUI ---
                    String responseBody = e.getResponseBodyAsString();
                    
                    // Verifica se é um erro 400 (Bad Request)
                    if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        try {
                            // Tenta parsear o erro da Meta
                            JsonNode errorNode = objectMapper.readTree(responseBody).path("error");
                            int errorSubcode = errorNode.path("error_subcode").asInt();

                            // Subcódigo 2593002 = "Message template not found"
                            if (errorSubcode == 2593002) {
                                logger.warn("Template '{}' não foi encontrado na Meta API (subcódigo {}). " +
                                            "Considerando como sucesso e removendo do banco local.", templateName, errorSubcode);
                                // Se não existe na Meta, o objetivo foi alcançado. Deleta localmente.
                                clientTemplateRepository.delete(clientTemplate);
                                return Mono.just(new ApiResponse(true, "Template já não existia na Meta, removido do sistema local.", null));
                            }
                        } catch (JsonProcessingException jsonException) {
                            logger.error("Erro ao parsear o corpo do erro da Meta API: {}", responseBody, jsonException);
                            // Cai para o tratamento de erro genérico abaixo
                        }
                    }
                    
                    // Para todos os outros erros da WebClient, relança como antes
                    logger.error("Erro da API Meta (WebClientResponseException) ao deletar template '{}': Status={}, Body={}",
                                templateName, e.getStatusCode(), responseBody);
                    return Mono.error(new BusinessException("Falha ao deletar template (API Meta): " + responseBody));
                });
    }

    @Override
    @Transactional
    public Mono<TemplateSyncResponse> syncTemplatesFromMeta(User user) {
        Company currentCompany = getCompanyFromUser(user);
        
        logger.info("Iniciando sincronização de templates para a empresa ID {}", currentCompany.getId());

        return this.fetchTemplatesForWaba(getCompanyWabaId(currentCompany), getBspWebClient())
            .collectList()
            .flatMap(metaTemplates -> {
                logger.info("Sincronização: {} templates encontrados na API da Meta para a empresa ID {}.", metaTemplates.size(), currentCompany.getId());
                AtomicInteger importedCount = new AtomicInteger(0);
                AtomicInteger updatedCount = new AtomicInteger(0);
                AtomicInteger alreadySyncedCount = new AtomicInteger(0);
                List<String> processedTemplates = new ArrayList<>();

                for (TemplateInfoResponse metaTemplate : metaTemplates) {
                    if (metaTemplate.getName() == null || metaTemplate.getLanguage() == null) continue;
                    
                    String templateName = metaTemplate.getName().trim();
                    String language = metaTemplate.getLanguage().trim();

                    try {
                        ClientTemplate template = clientTemplateRepository
                                .findByCompanyAndTemplateNameAndLanguage(currentCompany, templateName, language)
                                .orElseGet(() -> {
                                    importedCount.incrementAndGet();
                                    processedTemplates.add(templateName);
                                    ClientTemplate newTemplate = new ClientTemplate();
                                    newTemplate.setCompany(currentCompany);
                                    return newTemplate;
                                });
                        
                        boolean needsUpdate = !template.getStatus().equalsIgnoreCase(metaTemplate.getStatus());
                        if (template.getId() != null && needsUpdate) {
                             updatedCount.incrementAndGet();
                             if (!processedTemplates.contains(templateName)) processedTemplates.add(templateName);
                        } else if (template.getId() != null) {
                             alreadySyncedCount.incrementAndGet();
                        }

                        if (template.getId() == null || needsUpdate) {
                            updateTemplateEntityFromMetaInfo(template, metaTemplate);
                            clientTemplateRepository.save(template);
                        }
                    } catch (Exception e) {
                        logger.error("Falha ao processar a sincronização do template '{}': {}", templateName, e.getMessage());
                    }
                }

                return Mono.just(TemplateSyncResponse.builder()
                        .totalFoundInMeta(metaTemplates.size())
                        .importedCount(importedCount.get())
                        .updatedCount(updatedCount.get())
                        .alreadySyncedCount(alreadySyncedCount.get())
                        .processedTemplates(processedTemplates)
                        .build());
            });
    }

    @Override
    @Transactional
    public Mono<TemplatePushResponse> pushLocalTemplatesToMeta(Long companyId, User user) {
        
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            return Mono.error(new AccessDeniedException("Apenas administradores BSP podem enviar templates locais para a Meta."));
        }

        Company targetCompany = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));

        logger.info("Admin ID {}: Iniciando envio de templates locais para a Meta para a empresa ID {}", user.getId(), companyId);

        return this.fetchTemplatesForWaba(getCompanyWabaId(targetCompany), getBspWebClient())
            .collectList()
            .flatMap(metaTemplates -> {
                Set<String> metaTemplateKeys = metaTemplates.stream()
                        .map(t -> t.getName().toLowerCase() + ":" + t.getLanguage().toLowerCase())
                        .collect(Collectors.toSet());

                logger.debug("Encontrados {} templates na Meta para a empresa {}: {}", metaTemplates.size(), companyId, metaTemplateKeys);

                List<ClientTemplate> localTemplates = clientTemplateRepository.findAllByCompany(targetCompany);
                logger.debug("Encontrados {} templates no banco local para a empresa {}", localTemplates.size(), companyId);

                List<String> errors = new ArrayList<>();
                List<Mono<ApiResponse>> submissionMonos = localTemplates.stream()
                    .filter(localTemplate -> !metaTemplateKeys.contains(localTemplate.getTemplateName().toLowerCase() + ":" + localTemplate.getLanguage().toLowerCase()))
                    .map(localTemplate -> {
                        try {
                            List<CreateTemplateRequest.TemplateComponentDefinition> components = objectMapper.readValue(
                                    localTemplate.getComponentsJson(), new TypeReference<>() {});

                            CreateTemplateRequest createRequest = new CreateTemplateRequest();
                            createRequest.setName(localTemplate.getTemplateName());
                            createRequest.setLanguage(localTemplate.getLanguage());
                            createRequest.setCategory(localTemplate.getCategory());
                            createRequest.setComponents(components);

                            return this.createTemplate(createRequest, user)
                                    .map(savedTemplate -> new ApiResponse(true, savedTemplate.getTemplateName(), null))
                                    .onErrorResume(e -> Mono.just(new ApiResponse(false, "Erro ao submeter " + localTemplate.getTemplateName() + ": " + e.getMessage(), null)));
                        } catch (Exception e) {
                            errors.add("Erro ao preparar '" + localTemplate.getTemplateName() + "': " + e.getMessage());
                            return Mono.just(new ApiResponse(false, "Erro ao preparar " + localTemplate.getTemplateName(), null));
                        }
                    }).collect(Collectors.toList());

                return Flux.merge(submissionMonos).collectList().map(results -> {
                    long submittedCount = results.stream().filter(ApiResponse::isSuccess).count();
                    List<String> submittedNames = results.stream().filter(ApiResponse::isSuccess).map(ApiResponse::getMessage).collect(Collectors.toList());
                    errors.addAll(results.stream().filter(r -> !r.isSuccess()).map(ApiResponse::getMessage).collect(Collectors.toList()));
                    
                    return TemplatePushResponse.builder()
                            .companyId(companyId)
                            .totalFoundLocally(localTemplates.size())
                            .totalFoundInMeta(metaTemplates.size())
                            .submittedCount((int) submittedCount)
                            .alreadySyncedCount(localTemplates.size() - submissionMonos.size())
                            .submittedTemplates(submittedNames)
                            .errors(errors)
                            .build();
                });
            });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientTemplate> findTemplatesByNameForUser(String templateName, User user) {
        Company company = getCompanyFromUser(user);
        if (company == null) {
             if (user.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
                 // Um admin pode precisar buscar um template pelo nome em todas as empresas.
                 // Essa lógica seria mais complexa. Por enquanto, focamos no usuário da empresa.
                 logger.warn("Busca de template por nome para BSP_ADMIN sem empresa alvo não implementada.");
                 return Collections.emptyList();
             }
            throw new BusinessException("Usuário não está associado a uma empresa.");
        }
        
        logger.debug("Buscando templates com nome '{}' para a empresa ID {}", templateName, company.getId());
        return clientTemplateRepository.findByCompanyAndTemplateName(company, templateName);
    }

    // Helper para criar WebClient com token do BSP
    private WebClient getBspWebClient() {
        
        if (bspSystemUserAccessToken == null || bspSystemUserAccessToken.isBlank()) {
            throw new BusinessException("Token de System User do BSP não configurado.");
        }
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }

    // Helper para obter WABA ID da company
    private String getCompanyWabaId(Company company) {
        if (company == null || company.getMetaWabaId() == null || company.getMetaWabaId().isBlank()) {
            throw new BusinessException("WABA ID da Meta não configurado para a empresa.");
        }
        return company.getMetaWabaId();
    }

    private Company getCompanyFromUser(User user) {
        if (user == null) {
            throw new BusinessException("Usuário inválido fornecido para obter empresa.");
        }
        // Se o usuário for um BSP Admin, ele pode não ter uma empresa associada.
        // A lógica de negócio deve tratar isso no método que chama getCompanyFromUser.
        // Por exemplo, um admin pode precisar fornecer um ID de empresa alvo.
        if (user.getCompany() == null) {
            // Se o usuário não for um admin, ele obrigatoriamente deveria ter uma empresa.
            if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
                logger.error("Usuário não-admin (ID: {}) não está associado a nenhuma empresa.", user.getId());
                throw new BusinessException("Usuário não está associado a uma empresa. Contate o suporte.");
            }
            // Para um admin, retornamos nulo, e o método chamador decide o que fazer.
            return null;
        }
        return user.getCompany();
    }

    private Flux<TemplateInfoResponse> fetchTemplatesForWaba(String wabaId, WebClient webClient) {
        String endpoint = "/" + wabaId + "/message_templates";
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpoint)
                        .queryParam("fields", "id,name,status,category,language,components{type,format,text,buttons{type,text,url,phone_number}}")
                        .queryParam("limit", "250").build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                            .doOnNext(errorBody -> logger.error("Erro API Meta ao listar templates da WABA {}: Status={}, Body={}", wabaId, clientResponse.statusCode(), errorBody))
                            .then(Mono.empty())) // Continua para próxima WABA em caso de erro em uma
                .bodyToMono(JsonNode.class)
                .flatMapMany(responseNode -> {
                    if (responseNode.has("data") && responseNode.get("data").isArray()) {
                        return Flux.fromIterable(responseNode.get("data")).map(this::mapJsonNodeToTemplateInfoResponse);
                    }
                    return Flux.empty();
                })
                .onErrorResume(e -> { // Captura outros erros e continua
                    logger.error("Erro inesperado ao processar templates da WABA {}: {}", wabaId, e.getMessage());
                    return Flux.error(e);
                });
    }

    // --- Método Helper para Mapeamento ---
    private WhatsAppBusinessApiCreateTemplateRequest mapToMetaCreateTemplateRequest(CreateTemplateRequest request) {
        // Validações básicas
        if (request.getName() == null || request.getLanguage() == null || request.getCategory() == null || request.getComponents() == null || request.getComponents().isEmpty()) {
             throw new IllegalArgumentException("Campos obrigatórios (name, language, category, components) ausentes na requisição de criação de template.");
        }

         WhatsAppBusinessApiCreateTemplateRequest.WhatsAppBusinessApiCreateTemplateRequestBuilder metaBuilder = WhatsAppBusinessApiCreateTemplateRequest.builder()
                .name(request.getName())
                .language(request.getLanguage())
                .category(request.getCategory().toUpperCase()) // Garante maiúsculas
                .allowCategoryChange(request.isAllowCategoryChange());

        for (CreateTemplateRequest.TemplateComponentDefinition compDef : request.getComponents()) {
             if (compDef.getType() == null) throw new IllegalArgumentException("Tipo do componente não pode ser nulo.");

            WhatsAppBusinessApiCreateTemplateRequest.ComponentDefinition.ComponentDefinitionBuilder compBuilder =
                    WhatsAppBusinessApiCreateTemplateRequest.ComponentDefinition.builder()
                            .type(compDef.getType().toUpperCase()) // HEADER, BODY, FOOTER, BUTTONS
                            .format(compDef.getFormat() != null ? compDef.getFormat().toUpperCase() : null) // TEXT, IMAGE, ...
                            .text(compDef.getText());

            // Mapeia Exemplos (se existirem)
            if (compDef.getExample() != null) {
                
                WhatsAppBusinessApiCreateTemplateRequest.ExampleDefinition.ExampleDefinitionBuilder exMetaBuilder =
                        WhatsAppBusinessApiCreateTemplateRequest.ExampleDefinition.builder();
                
                String componentTypeUpper = compDef.getType().toUpperCase();
                String componentFormatUpper = compDef.getFormat() != null ? compDef.getFormat().toUpperCase() : null;

                boolean exampleShouldBeAdded  = false;

                if ("BODY".equals(componentTypeUpper)) {
                    if (compDef.getExample().getBodyText() != null && !compDef.getExample().getBodyText().isEmpty()) {
                        exMetaBuilder.bodyText(compDef.getExample().getBodyText());
                        exampleShouldBeAdded = true; // Marcamos que há conteúdo relevante
                    }
                    // Não adicionamos header_text ou header_handle
                } else if ("HEADER".equals(componentTypeUpper)) {
                    if ("TEXT".equals(componentFormatUpper)) {
                        if (compDef.getExample().getHeaderText() != null && !compDef.getExample().getHeaderText().isEmpty()) {
                            exMetaBuilder.headerText(compDef.getExample().getHeaderText());
                            exampleShouldBeAdded = true;
                        }
                    } else if (compDef.getFormat() != null && List.of("IMAGE", "VIDEO", "DOCUMENT").contains(componentFormatUpper)) {
                        if (compDef.getExample() != null && compDef.getExample().getHeaderHandle() != null && !compDef.getExample().getHeaderHandle().isEmpty()) {
                            exMetaBuilder.headerHandle(compDef.getExample().getHeaderHandle());
                            exampleShouldBeAdded = true;
                        }
                    }
                }
                
                if (exampleShouldBeAdded) {
                    compBuilder.example(exMetaBuilder.build());
                }
            }

            // Mapeia Botões (se for componente BUTTONS)
            if ("BUTTONS".equalsIgnoreCase(compDef.getType()) && compDef.getButtons() != null) {
                for (CreateTemplateRequest.ButtonDefinition btnDef : compDef.getButtons()) {
                    if (btnDef.getType() == null || btnDef.getText() == null) 
                        throw new IllegalArgumentException("Botão deve ter 'type' e 'text'.");

                    WhatsAppBusinessApiCreateTemplateRequest.ButtonDefinition.ButtonDefinitionBuilder metaBtnBuilder =
                            WhatsAppBusinessApiCreateTemplateRequest.ButtonDefinition.builder()
                                    .type(btnDef.getType().toUpperCase()) // QUICK_REPLY, URL, PHONE_NUMBER, ...
                                    .text(btnDef.getText());

                    switch (btnDef.getType().toUpperCase()) {
                        case "URL":
                            if (btnDef.getUrl() == null) 
                                throw new IllegalArgumentException("...");
                            metaBtnBuilder.url(btnDef.getUrl());
                            if (btnDef.getExample() != null && !btnDef.getExample().isEmpty()) {
                                metaBtnBuilder.example(btnDef.getExample());
                            }
                            break;
                        case "PHONE_NUMBER":
                            if (btnDef.getPhoneNumber() == null) 
                                throw new IllegalArgumentException("...");
                            metaBtnBuilder.phoneNumber(btnDef.getPhoneNumber());
                            break;
                        case "OTP":
                            metaBtnBuilder.type("OTP"); // Define o tipo principal para a Meta
                            if (btnDef.getOtpType() == null) 
                                throw new IllegalArgumentException("Botão OTP deve ter 'otpType'.");
                            metaBtnBuilder.otpType(btnDef.getOtpType().toUpperCase()); // Mapeia para o campo otp_type da Meta

                            if ("COPY_CODE".equalsIgnoreCase(btnDef.getOtpType())) {
                                if (btnDef.getExample() != null && !btnDef.getExample().isEmpty()) {
                                    metaBtnBuilder.example(btnDef.getExample());
                                } else {
                                    throw new IllegalArgumentException("Botão OTP do tipo COPY_CODE deve ter um 'example' com o código.");
                                }
                            } else if ("ONE_TAP_AUTOFIL".equalsIgnoreCase(btnDef.getOtpType())) {
                                // Mapear packageNameAndroid e signatureHashIos
                                // metaBtnBuilder.packageNameAndroid(btnDef.getPackageNameAndroid());
                                // metaBtnBuilder.signatureHashIos(btnDef.getSignatureHashIos());
                            } else {
                                throw new IllegalArgumentException("Subtipo de OTP desconhecido: " + btnDef.getOtpType());
                            }
                            break;
                        case "QUICK_REPLY":
                            // Na definição de template, QUICK_REPLY geralmente só tem 'text'.
                            // O payload é definido ao enviar a mensagem interativa, não na definição do template.
                            break;
                        case "FLOW":
                            metaBtnBuilder.type("FLOW");
                            metaBtnBuilder.text(btnDef.getText());
                            // O cliente deve fornecer ou o nome ou o ID
                            if (StringUtils.hasText(btnDef.getFlowName())) {
                                metaBtnBuilder.flowName(btnDef.getFlowName());
                            } else if (StringUtils.hasText(btnDef.getFlowId())) {
                                metaBtnBuilder.flowId(btnDef.getFlowId());
                            } else {
                                throw new BusinessException("Botão do tipo FLOW deve ter 'flowName' ou 'flowId'.");
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Tipo de botão desconhecido na definição: " + btnDef.getType());
                    }
                    compBuilder.button(metaBtnBuilder.build());
                }
            }
            metaBuilder.component(compBuilder.build());
        }

        return metaBuilder.build();
    }

    private TemplateInfoResponse mapJsonNodeToTemplateInfoResponse(JsonNode node) {
        if (node == null) 
            return null;
        TemplateInfoResponse info = new TemplateInfoResponse();
        info.setId(node.path("id").asText(null));
        info.setName(node.path("name").asText(null));
        info.setStatus(node.path("status").asText(null));
        info.setCategory(node.path("category").asText(null));
        info.setLanguage(node.path("language").asText(null));

        if (node.has("components") && node.get("components").isArray()) {
            List<TemplateInfoResponse.TemplateComponentResponse> components = new ArrayList<>();
            for (JsonNode compNode : node.get("components")) {
                TemplateInfoResponse.TemplateComponentResponse comp = new TemplateInfoResponse.TemplateComponentResponse();
                comp.setType(compNode.path("type").asText(null));
                comp.setFormat(compNode.path("format").asText(null));
                comp.setText(compNode.path("text").asText(null));

                if (compNode.has("buttons") && compNode.get("buttons").isArray()) {
                    List<TemplateInfoResponse.ButtonResponse> buttons = new ArrayList<>();
                    for (JsonNode btnNode : compNode.get("buttons")) {
                        TemplateInfoResponse.ButtonResponse btn = new TemplateInfoResponse.ButtonResponse();
                        btn.setType(btnNode.path("type").asText(null));
                        btn.setText(btnNode.path("text").asText(null));
                        btn.setUrl(btnNode.path("url").asText(null));
                        btn.setPhoneNumber(btnNode.path("phone_number").asText(null));
                        btn.setCouponCode(btnNode.path("coupon_code").asText(null));
                        // Adicionar outros campos de botão se necessário
                        buttons.add(btn);
                    }
                     comp.setButtons(buttons);
                }
                components.add(comp);
            }
            info.setComponents(components);
        }
        return info;
    }

    // Método helper para mapear do DTO de resposta da Meta para a nossa entidade
    private void updateTemplateEntityFromMetaInfo(ClientTemplate entity, TemplateInfoResponse metaInfo) {
        entity.setTemplateName(metaInfo.getName());
        entity.setLanguage(metaInfo.getLanguage());
        entity.setCategory(metaInfo.getCategory().toUpperCase());
        entity.setStatus(metaInfo.getStatus().toUpperCase());
        entity.setMetaTemplateId(metaInfo.getId());
        // Serializa os componentes recebidos da Meta para o campo JSON
        try {
            if (metaInfo.getComponents() != null) {
                entity.setComponentsJson(objectMapper.writeValueAsString(metaInfo.getComponents()));
            }
        } catch (JsonProcessingException e) {
            logger.error("Erro ao serializar componentes para o template '{}' durante a sincronização.", metaInfo.getName(), e);
            entity.setComponentsJson("{\"error\":\"Failed to serialize components\"}");
        }
    }
}
