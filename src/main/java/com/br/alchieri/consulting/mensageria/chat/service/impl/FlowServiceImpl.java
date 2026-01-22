package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowJsonUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowMetricResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowSyncResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowMetricName;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MetricGranularity;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.service.FlowService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowServiceImpl implements FlowService {

    private final FlowRepository flowRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final BillingService billingService;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    @Value("${whatsapp.flow.public-key}")
    private String flowPublicKey;

    @Value("${meta.app.id}")
    private String bspAppId;

    @Override
    public Page<Flow> listFlowsByCompany(Company company, Pageable pageable) {
        return flowRepository.findByCompany(company, pageable);
    }

    @Override
    public Optional<Flow> getFlowByIdAndCompany(Long flowId, Company company) {
        return flowRepository.findByIdAndCompany(flowId, company);
    }

    @SuppressWarnings("null")
    @Override
    @Transactional
    public Mono<Flow> createFlow(FlowRequest request, Company company, boolean publish) {
        if (company.getMetaWabaId() == null) {
            return Mono.error(new BusinessException("Empresa não tem um WABA ID configurado."));
        }

        String endpoint = "/" + company.getMetaWabaId() + "/flows";
        String normalizedMetaName = normalizeMetaName(request.getName());

        String flowJsonString;
        try {
            JsonNode flowJsonNode = request.getJsonDefinition();
            if (flowJsonNode instanceof ObjectNode) {
                ((ObjectNode) flowJsonNode).put("version", request.getJsonVersion());
                ((ObjectNode) flowJsonNode).remove("name");
            }
            flowJsonString = objectMapper.writeValueAsString(flowJsonNode);
        } catch (JsonProcessingException e) {
            return Mono.error(new BusinessException("JSON de definição do Flow é inválido.", e));
        }

        Map<String, Object> metaRequestBody = Map.of(
            "name", normalizedMetaName,
            "categories", request.getCategories(),
            "flow_json", flowJsonString,
            "publish", publish
        );

        log.info("Criando Flow '{}' na Meta para WABA {}", normalizedMetaName, company.getMetaWabaId());

        return getBspWebClient().post().uri(endpoint).body(BodyInserters.fromValue(metaRequestBody)).retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Erro da API Meta ao criar Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                        return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), 
                            "Erro da API Meta", null, errorBody.getBytes(), null));
                    })
            )
            .bodyToMono(JsonNode.class)
            .flatMap(responseNode -> {
                String metaFlowId = responseNode.path("id").asText(null);
                if (metaFlowId == null) {
                    return Mono.error(new BusinessException("API da Meta não retornou um ID para o Flow criado."));
                }

                Flow flow = new Flow();
                flow.setMetaFlowId(metaFlowId);
                flow.setName(request.getName());
                flow.setCompany(company);
                flow.setJsonVersion(request.getJsonVersion());
                flow.setDataApiVersion(request.getDataApiVersion());
                flow.setEndpointUri(request.getEndpointUri());
                flow.setDraftJsonDefinition(flowJsonString);
                try {
                    flow.setCategoriesJson(objectMapper.writeValueAsString(request.getCategories()));
                } catch (JsonProcessingException e) { /* log */ }

                flow.setStatus(publish ? FlowStatus.PUBLISHED : FlowStatus.DRAFT);
                flow.setPublishedJsonDefinition(publish ? flowJsonString : null);
                flow.setHasUnpublishedChanges(!publish);
                Flow savedFlow = flowRepository.save(flow);

                // Se endpoint_uri foi fornecido, faz uma chamada de ATUALIZAÇÃO de metadados
                if (StringUtils.hasText(request.getEndpointUri())) {
                    FlowUpdateRequest updateRequest = new FlowUpdateRequest();
                    updateRequest.setEndpointUri(request.getEndpointUri());
                    if (StringUtils.hasText(request.getDataApiVersion())) {
                        updateRequest.setDataApiVersion(request.getDataApiVersion());
                    }
                    // Retorna o resultado da chamada de atualização
                    return this.updateFlowMetadata(savedFlow.getId(), updateRequest, company);
                } else {
                    return Mono.just(savedFlow);
                }
            });
    }

    @Override
    @Transactional
    public Mono<Flow> updateFlowMetadata(Long flowId, FlowUpdateRequest request, Company company) {
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
            .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado."));
        
        if (flow.getMetaFlowId() == null) {
            return Mono.error(new BusinessException("Este Flow não existe na Meta."));
        }

        if (flow.getStatus() == FlowStatus.DEPRECATED) {
            return Mono.error(new BusinessException("Não é possível editar um Flow que já foi desativado (deprecated)."));
        }

        String endpoint = "/" + flow.getMetaFlowId();
        
        ObjectNode requestBody = objectMapper.createObjectNode();
        boolean hasChanges = false;
        if (StringUtils.hasText(request.getName())) {
             requestBody.put("name", normalizeMetaName(request.getName())); hasChanges = true;
        }
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
             requestBody.set("categories", objectMapper.valueToTree(request.getCategories())); hasChanges = true;
        }
        if (StringUtils.hasText(request.getEndpointUri())) {
            requestBody.put("endpoint_uri", request.getEndpointUri()); hasChanges = true;
        }
        if (StringUtils.hasText(request.getDataApiVersion())) {
            requestBody.put("data_api_version", request.getDataApiVersion()); hasChanges = true;
        }
        if (!hasChanges) return Mono.just(flow);

        log.info("Atualizando metadados do Flow '{}' (Meta ID {})", flow.getName(), flow.getMetaFlowId());

        return getBspWebClient().post().uri(endpoint).body(BodyInserters.fromValue(requestBody)).retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.createException().flatMap(Mono::error))
            .bodyToMono(JsonNode.class)
            .map(responseNode -> {
                if (responseNode.path("success").asBoolean(false)) {
                    // Atualiza os campos locais
                    if (StringUtils.hasText(request.getName())) flow.setName(request.getName());
                    if (StringUtils.hasText(request.getEndpointUri())) flow.setEndpointUri(request.getEndpointUri());
                    if (StringUtils.hasText(request.getDataApiVersion())) flow.setDataApiVersion(request.getDataApiVersion());
                    try {
                        if (request.getCategories() != null) flow.setCategoriesJson(objectMapper.writeValueAsString(request.getCategories()));
                    } catch (JsonProcessingException e) { log.error("Erro ao serializar categorias ao atualizar metadata", e); }
                    log.info("Metadados do Flow '{}' atualizados com sucesso.", flow.getName());
                    return flowRepository.save(flow);
                } else {
                    throw new BusinessException("API da Meta falhou ao atualizar metadados do Flow.");
                }
            });
    }

    @Override
    @Transactional
    public Mono<Flow> updateFlowJson(Long flowId, FlowJsonUpdateRequest request, Company company) {
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
            .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado."));

        if (flow.getMetaFlowId() == null) {
            return Mono.error(new BusinessException("Este Flow não existe na Meta."));
        }

        // Regra: Não se pode editar um Flow que foi desativado (deprecated).
        if (flow.getStatus() == FlowStatus.DEPRECATED) {
            return Mono.error(new BusinessException("Não é possível editar um Flow que já foi desativado (deprecated)."));
        }

        String endpoint = "/" + flow.getMetaFlowId() + "/assets";
        log.info("Atualizando JSON do Flow '{}' (Meta ID {})", flow.getName(), flow.getMetaFlowId());

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("name", "flow.json");
        builder.part("asset_type", "FLOW_JSON");
        try {
            JsonNode flowJsonNode = request.getJsonDefinition();
            if (flowJsonNode instanceof ObjectNode) {
                ((ObjectNode) flowJsonNode).put("version", request.getJsonVersion());
            }
            String newJsonDefinition = objectMapper.writeValueAsString(flowJsonNode);
            ByteArrayResource jsonResource = new ByteArrayResource(newJsonDefinition.getBytes());
            builder.part("file", jsonResource).filename("flow.json").contentType(MediaType.APPLICATION_JSON);

            return getBspWebClient().post().uri(endpoint).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.createException().flatMap(Mono::error))
                .bodyToMono(JsonNode.class)
                .map(responseNode -> {
                    if (responseNode.path("success").asBoolean(false)) {
                        flow.setDraftJsonDefinition(newJsonDefinition);
                        flow.setJsonVersion(request.getJsonVersion());
                        flow.setHasUnpublishedChanges(true);
                        flow.setStatus(FlowStatus.DRAFT); 
                        log.info("JSON do Flow '{}' atualizado com sucesso. Status alterado para DRAFT.", flow.getName());
                        return flowRepository.save(flow);
                    } else {
                        throw new BusinessException("API da Meta falhou ao atualizar o JSON do Flow.");
                    }
                });
        } catch (JsonProcessingException e) {
            return Mono.error(new BusinessException("JSON de definição do Flow é inválido.", e));
        }
    }

    @Override
    @Transactional
    public Mono<Flow> publishFlow(Long flowId, Company company) {
        
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
        .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado."));

        if (flow.getMetaFlowId() == null) {
            return Mono.error(new BusinessException("Flow não pode ser publicado pois não existe na Meta."));
        }
        if (flow.getStatus() == FlowStatus.PUBLISHED) {
            return Mono.just(flow);
        }

        // Checa o limite APENAS se o flow ainda não estiver publicado
        if (flow.getStatus() != FlowStatus.PUBLISHED) {
            if (!billingService.canCompanyCreateFlow(company)) {
                return Mono.error(new BusinessException("Limite de flows ativos excedido."));
            }
        }

        // Primeiro, busca o status mais recente da Meta para garantir que não há erros de validação.
        return this.fetchAndSyncFlowStatus(flowId, company)
            .flatMap(syncedFlow -> {
                if (syncedFlow.getStatus() == FlowStatus.DISABLED || syncedFlow.getValidationErrors() != null) {
                    log.error("Tentativa de publicar Flow ID {} que possui erros de validação: {}", flowId, syncedFlow.getValidationErrors());
                    return Mono.error(new BusinessException("O Flow não pode ser publicado pois contém erros de validação. Verifique o status para detalhes."));
                }

                // Se não houver erros, prossegue com a publicação.
                String endpoint = "/" + syncedFlow.getMetaFlowId() + "/publish";
                log.info("Publicando Flow '{}' (Meta ID {})", syncedFlow.getName(), syncedFlow.getMetaFlowId());

                return getBspWebClient().post().uri(endpoint).retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Erro da API Meta ao publicar Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                                // Salva os erros no banco
                                try {
                                    JsonNode errorNode = objectMapper.readTree(errorBody);
                                    syncedFlow.setValidationErrors(objectMapper.writeValueAsString(errorNode.path("error")));
                                } catch (JsonProcessingException e) {
                                    syncedFlow.setValidationErrors(errorBody);
                                }
                                flowRepository.save(syncedFlow);
                                return Mono.error(new BusinessException("Falha ao publicar Flow na Meta: " + errorBody));
                            })
                    )
                    .bodyToMono(JsonNode.class)
                    .map(responseNode -> {
                        if (responseNode.path("success").asBoolean(false)) {
                            syncedFlow.setStatus(FlowStatus.PUBLISHED);
                            syncedFlow.setPublishedJsonDefinition(syncedFlow.getDraftJsonDefinition());
                            syncedFlow.setHasUnpublishedChanges(false);
                            syncedFlow.setValidationErrors(null);
                            return flowRepository.save(syncedFlow);
                        } else {
                            throw new BusinessException("API da Meta falhou ao publicar o Flow.");
                        }
                    });
            });
    }

    @SuppressWarnings("null")
    @Override
    @Transactional
    public Mono<ApiResponse> deleteFlow(Long flowId, Company company) {
        
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
            .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado para esta empresa."));
        
        if (flow.getStatus() != FlowStatus.DRAFT) {
            return Mono.error(new BusinessException("Apenas Flows em status DRAFT podem ser excluídos. Para remover um Flow publicado, use o endpoint 'deprecate'."));
        }

        if (flow.getMetaFlowId() == null) { // É apenas um rascunho local
            flowRepository.delete(flow);
            return Mono.just(new ApiResponse(true, "Rascunho de Flow local deletado com sucesso.", null));
        }

        String endpoint = "/" + flow.getMetaFlowId();
        log.info("Deletando Flow '{}' (Meta ID {}) para a empresa ID {}", flow.getName(), flow.getMetaFlowId(), company.getId());
        
        return getBspWebClient().delete().uri(endpoint).retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Erro da API Meta ao deletar Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                        return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), "Erro ao deletar Flow na Meta", null, errorBody.getBytes(), null));
                    })
            )
            .bodyToMono(JsonNode.class)
            .flatMap(responseNode -> {
                if (responseNode.path("success").asBoolean(false)) {
                    flowRepository.delete(flow);
                    log.info("Flow ID local {} deletado com sucesso após confirmação da Meta.", flowId);
                    return Mono.just(new ApiResponse(true, "Flow deletado com sucesso.", null));
                }
                return Mono.error(new BusinessException("API da Meta falhou ao deletar o Flow."));
            });
    }

    @SuppressWarnings("null")
    @Override
    @Transactional
    public Mono<Flow> deprecateFlow(Long flowId, Company company) {
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
            .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado para esta empresa."));
        
        if (!List.of(FlowStatus.PUBLISHED, FlowStatus.THROTTLED, FlowStatus.BLOCKED).contains(flow.getStatus())) {
            if (flow.getStatus() == FlowStatus.DEPRECATED) {
                log.info("Flow ID {} já está DEPRECATED.", flowId);
                return Mono.just(flow);
            }
            return Mono.error(new BusinessException("Apenas Flows publicados, limitados (throttled) ou bloqueados podem ser desativados. Status atual: " + flow.getStatus()));
        }
        
        if (flow.getMetaFlowId() == null || flow.getStatus() != FlowStatus.PUBLISHED) {
            return Mono.error(new BusinessException("Apenas Flows publicados existentes na Meta podem ser desativados."));
        }

        if (flow.getStatus() == FlowStatus.DEPRECATED) {
            log.info("Flow ID {} já está desativado (deprecated). Nenhuma ação necessária.", flowId);
            return Mono.just(flow);
        }
        
        String endpoint = "/" + flow.getMetaFlowId() + "/deprecate";
        log.info("Desativando Flow '{}' (Meta ID {})", flow.getName(), flow.getMetaFlowId());

        return getBspWebClient().post().uri(endpoint).retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Erro da API Meta ao desativar Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                        return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), "Erro ao desativar Flow na Meta", null, errorBody.getBytes(), null));
                    })
            )
            .bodyToMono(JsonNode.class)
            .map(responseNode -> {
                if (responseNode.path("success").asBoolean(false)) {
                    flow.setStatus(FlowStatus.DEPRECATED);
                    log.info("Flow '{}' (Meta ID {}) desativado com sucesso.", flow.getName(), flow.getMetaFlowId());
                    return flowRepository.save(flow);
                } else {
                    throw new BusinessException("API da Meta falhou ao desativar o Flow.");
                }
            });
    }

    @SuppressWarnings("null")
    @Override
    @Transactional
    public Mono<Flow> fetchAndSyncFlowStatus(Long flowId, Company company) {
        // 1. Busca a entidade Flow do nosso banco de dados local
        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
            .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado para esta empresa."));

        // Se o Flow não tem um ID da Meta, ele é apenas um rascunho local, não há o que sincronizar.
        if (flow.getMetaFlowId() == null) {
            log.warn("Flow ID {} é um rascunho local (DRAFT) e não existe na Meta. Nenhum status para sincronizar.", flowId);
            return Mono.just(flow); // Retorna o estado atual sem chamar a Meta
        }

        String endpoint = "/" + flow.getMetaFlowId();
        log.info("Buscando status atualizado da Meta para o Flow '{}' (Meta ID {})", flow.getName(), flow.getMetaFlowId());

        // 2. Chama a API da Meta para obter os dados mais recentes
        return getBspWebClient().get()
            .uri(uriBuilder -> uriBuilder.path(endpoint)
                                        // Solicita os campos que queremos sincronizar
                                        .queryParam("fields", "id,name,status,validation_errors,categories,endpoint_uri")
                                        .build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Erro da API Meta ao buscar status do Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                        return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), "Erro ao buscar status do Flow na Meta", null, errorBody.getBytes(), null));
                    })
            )
            .bodyToMono(JsonNode.class)
            .map(responseNode -> {
                log.debug("Resposta da Meta para status do Flow ID {}: {}", flowId, responseNode);

                // 3. Mapeia a resposta da Meta e atualiza a nossa entidade Flow
                JsonNode validationErrorsNode = responseNode.path("validation_errors");
                if (validationErrorsNode.isArray() && !validationErrorsNode.isEmpty()) {
                    // Se a Meta retorna erros de validação, o Flow é considerado desativado
                    flow.setStatus(FlowStatus.DISABLED);
                    try {
                        flow.setValidationErrors(objectMapper.writeValueAsString(validationErrorsNode));
                    } catch (JsonProcessingException e) { 
                        log.error("Erro ao serializar erros de validação do Flow", e);
                    }
                    log.warn("Flow '{}' (Meta ID {}) possui erros de validação da Meta. Status atualizado para DISABLED.", flow.getName(), flow.getMetaFlowId());
                } else {
                    String metaStatus = responseNode.path("status").asText("UNKNOWN");
                    try {
                        // Tenta mapear o status da Meta para o nosso enum
                        flow.setStatus(FlowStatus.valueOf(metaStatus.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        log.warn("Status '{}' retornado pela Meta não é mapeado no enum FlowStatus. O status local não foi alterado.", metaStatus);
                    }
                    flow.setValidationErrors(null); // Limpa os erros se não houver mais
                }
                
                // Opcional: Sincronizar outros metadados que podem ter sido alterados na UI da Meta
                // Não sincronizamos o nome técnico da Meta, apenas o nosso nome amigável
                // flow.setName(responseNode.path("name").asText(flow.getName()));
                
                try {
                    if(responseNode.has("categories")){
                         flow.setCategoriesJson(objectMapper.writeValueAsString(responseNode.get("categories")));
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Não foi possível sincronizar as categorias do Flow ID {}: {}", flowId, e.getMessage());
                }
                flow.setEndpointUri(responseNode.path("endpoint_uri").asText(flow.getEndpointUri()));

                log.info("Status do Flow '{}' (Meta ID {}) sincronizado para: {}", flow.getName(), flow.getMetaFlowId(), flow.getStatus());
                
                // 4. Salva a entidade atualizada
                return flowRepository.save(flow);
            });
    }

    @Override
    @Transactional
    public Mono<FlowSyncResponse> syncFlowsFromMeta(Company company) {
        if (company.getMetaWabaId() == null) {
            return Mono.error(new BusinessException("Empresa não tem um WABA ID configurado para sincronizar Flows."));
        }

        String endpoint = "/" + company.getMetaWabaId() + "/flows";
        log.info("Iniciando sincronização de Flows para a empresa ID {}", company.getId());

        WebClient webClient = getBspWebClient();
        
        // 1. Buscar todos os Flows da Meta para esta WABA
        return webClient.get().uri(endpoint).retrieve()
            .bodyToMono(JsonNode.class)
            .flatMap(responseNode -> {
                JsonNode dataNode = responseNode.path("data");
                if (!dataNode.isArray()) {
                    log.warn("Nenhum Flow encontrado na Meta para a empresa ID {}", company.getId());
                    return Mono.just(FlowSyncResponse.builder().totalFoundInMeta(0).build());
                }

                List<JsonNode> metaFlows = new ArrayList<>();
                dataNode.forEach(metaFlows::add);

                log.info("{} Flows encontrados na Meta. Processando...", metaFlows.size());

                // 2. Buscar todos os Flows locais para comparar
                List<Flow> localFlows = flowRepository.findByCompany(company, Pageable.unpaged()).getContent();
                Map<String, Flow> localFlowsByMetaId = localFlows.stream()
                        .filter(f -> f.getMetaFlowId() != null)
                        .collect(Collectors.toMap(Flow::getMetaFlowId, f -> f));

                AtomicInteger importedCount = new AtomicInteger(0);
                AtomicInteger updatedCount = new AtomicInteger(0);
                List<String> processedFlows = new ArrayList<>();

                // 3. Iterar e sincronizar
                for (JsonNode metaFlow : metaFlows) {
                    String metaFlowId = metaFlow.path("id").asText(null);
                    String metaStatus = metaFlow.path("status").asText("UNKNOWN");
                    String metaName = metaFlow.path("name").asText("Unnamed Flow");

                    if (metaFlowId == null) continue;

                    Flow localFlow = localFlowsByMetaId.get(metaFlowId);

                    if (localFlow == null) {
                        // Flow da Meta não existe localmente -> Importar
                        log.info("Importando novo Flow da Meta: Name='{}', MetaID='{}'", metaName, metaFlowId);
                        Flow newFlow = new Flow();
                        newFlow.setCompany(company);
                        updateFlowEntityFromMetaJson(newFlow, metaFlow);
                        flowRepository.save(newFlow);
                        importedCount.incrementAndGet();
                        processedFlows.add(metaName);
                    } else {
                        // Flow já existe, verificar se precisa de atualização
                        FlowStatus localStatus = FlowStatus.valueOf(metaStatus.toUpperCase());
                        if (localFlow.getStatus() != localStatus) {
                            log.info("Atualizando status do Flow '{}' (MetaID: {}) de {} para {}",
                                    localFlow.getName(), metaFlowId, localFlow.getStatus(), localStatus);
                            localFlow.setStatus(localStatus);
                            flowRepository.save(localFlow);
                            updatedCount.incrementAndGet();
                            processedFlows.add(metaName);
                        }
                    }
                }

                return Mono.just(FlowSyncResponse.builder()
                        .totalFoundInMeta(metaFlows.size())
                        .importedCount(importedCount.get())
                        .updatedCount(updatedCount.get())
                        .alreadySyncedCount(metaFlows.size() - importedCount.get() - updatedCount.get())
                        .processedFlows(processedFlows)
                        .build());
            });
    }

    @SuppressWarnings("null")
    @Override
    public Mono<FlowMetricResponse> getFlowMetrics(Long flowId, Company company,
                                                   FlowMetricName metricName, MetricGranularity granularity,
                                                   LocalDate since, LocalDate until) {

        Flow flow = flowRepository.findByIdAndCompany(flowId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Flow com ID " + flowId + " não encontrado para esta empresa."));

        if (flow.getMetaFlowId() == null) {
            return Mono.error(new BusinessException("Este Flow não existe na Meta e não possui métricas."));
        }

        String endpointPath = "/" + flow.getMetaFlowId();
        String fieldsParam = String.format("metric.name(%s).granularity(%s)",
                                           metricName.name(), granularity.name());

        // Adiciona since e until à string de fields, se fornecidos
        if (since != null) {
            fieldsParam += ".since(" + since.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")";
        }
        if (until != null) {
            fieldsParam += ".until(" + until.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")";
        }
        final String finalFieldsParam = fieldsParam; // Variável final para usar no lambda

        log.info("Buscando métricas para o Flow '{}' (Meta ID {}): {}", flow.getName(), flow.getMetaFlowId(), finalFieldsParam);

        return getBspWebClient().get()
                .uri(uriBuilder -> uriBuilder.path(endpointPath)
                                             .queryParam("fields", finalFieldsParam)
                                             .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Erro da API Meta ao buscar métricas do Flow: Status={}, Body={}", clientResponse.statusCode(), errorBody);
                            // A API retorna uma exceção específica para "poucos dados", vamos tratá-la
                            if (errorBody.contains("not enough data")) {
                                 return Mono.error(new BusinessException("Não há dados de métricas suficientes para este Flow e período. São necessários pelo menos 250 eventos."));
                            }
                            return Mono.error(WebClientResponseException.create(clientResponse.statusCode().value(), "Erro ao buscar métricas na Meta", null, errorBody.getBytes(), null));
                        })
                )
                .bodyToMono(FlowMetricResponse.class) // Desserializa diretamente para o nosso DTO
                .doOnSuccess(response -> log.info("Métricas para o Flow ID {} recuperadas com sucesso.", response.getId()));
    }

    // --- MÉTODOS HELPER ---

    private String normalizeMetaName(String friendlyName) {
        if (!StringUtils.hasText(friendlyName)) {
            return "flow_" + UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = java.text.Normalizer.normalize(friendlyName, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private WebClient getBspWebClient() {
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }

    // Novo método helper para mapear do JSON da Meta para a nossa entidade
    private void updateFlowEntityFromMetaJson(Flow entity, JsonNode metaJson) {
        entity.setMetaFlowId(metaJson.path("id").asText());
        entity.setName(metaJson.path("name").asText("Flow Sincronizado")); // Usa o nome da Meta
        
        String metaStatus = metaJson.path("status").asText("UNKNOWN").toUpperCase();
        try {
            entity.setStatus(FlowStatus.valueOf(metaStatus));
        } catch (IllegalArgumentException e) {
            log.warn("Status de Flow desconhecido da Meta: '{}'. Marcando como DISABLED.", metaStatus);
            entity.setStatus(FlowStatus.DISABLED);
        }
        
        // A API de listagem não retorna o JSON de definição, então marcamos como placeholder.
        // O usuário precisaria "editar" e colar o JSON para ter a definição completa.
        if (entity.getDraftJsonDefinition() == null) {
             entity.setDraftJsonDefinition("{\"status\":\"Sincronizado da Meta. Definição completa não disponível via API de listagem.\"}");
        }
        
        JsonNode validationErrorsNode = metaJson.path("validation_errors");
        if (validationErrorsNode.isArray() && !validationErrorsNode.isEmpty()) {
            try {
                entity.setValidationErrors(objectMapper.writeValueAsString(validationErrorsNode));
            } catch (JsonProcessingException e) { /* log */ }
        } else {
            entity.setValidationErrors(null);
        }
    }
}
