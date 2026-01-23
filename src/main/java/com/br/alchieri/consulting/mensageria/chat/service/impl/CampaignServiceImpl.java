package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledMessage;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledCampaignRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledMessageRepository;
import com.br.alchieri.consulting.mensageria.chat.service.CallbackService;
import com.br.alchieri.consulting.mensageria.chat.service.CampaignService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private final WebClient.Builder webClientBuilder;
    private final ScheduledCampaignRepository campaignRepository;
    private final ScheduledMessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper; // Para serializar parâmetros
    private final CallbackService callbackService;
    private final BillingService billingService;

    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Override
    @Transactional
    public ScheduledCampaign scheduleNewCampaign(ScheduleCampaignRequest request, User creator) {
     
        Company company = creator.getCompany();

        if (!billingService.canCompanyExecuteCampaign(company)) {
            throw new BusinessException("Limite mensal de execução de campanhas excedido.");
        }
     
        if (company == null && !creator.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            throw new BusinessException("Usuário não está associado a uma empresa.");
        }
        // Se for admin, a company pode ser nula, o que causaria erro.
        // A lógica de negócio precisaria definir se um admin pode criar campanha para uma empresa alvo.
        // Por agora, vamos assumir que apenas usuários de empresa podem criar campanhas.
        if (company == null) {
            throw new BusinessException("Apenas usuários de empresas podem criar campanhas.");
        }

        // 1. Encontrar os contatos alvo
        List<Contact> targetContacts = findTargetContacts(request, company);
        if (targetContacts.isEmpty()) {
            throw new BusinessException("Nenhum contato válido encontrado para os critérios fornecidos.");
        }

        // 2. Criar a entidade da campanha
        ScheduledCampaign campaign = new ScheduledCampaign();
        campaign.setCompany(company);
        campaign.setCreatedByUser(creator);
        campaign.setCampaignName(request.getCampaignName());
        campaign.setTemplateName(request.getTemplateName());
        campaign.setLanguageCode(request.getLanguageCode());
        campaign.setScheduledAt(request.getScheduledAt());
        campaign.setStatus(ScheduledCampaign.CampaignStatus.PENDING);
        campaign.setTotalMessages(targetContacts.size());

        // Armazena o mapeamento dos componentes como JSON
        try {
            campaign.setComponentMappingsJson(objectMapper.writeValueAsString(request.getComponents()));
        } catch (JsonProcessingException e) {
            throw new BusinessException("Mapeamento de componentes inválido.", e);
        }

        // 3. Criar as mensagens individuais agendadas
        List<ScheduledMessage> messages = new ArrayList<>();
        for (Contact contact : targetContacts) {
            ScheduledMessage msg = new ScheduledMessage();
            msg.setCampaign(campaign);
            msg.setContact(contact);
            msg.setScheduledAt(request.getScheduledAt());
            msg.setStatus(ScheduledMessage.MessageStatus.PENDING);
            messages.add(msg);
        }

        campaign.setMessages(messages);

        // 4. Salvar tudo (CascadeType.ALL cuidará de salvar as mensagens)
        ScheduledCampaign savedCampaign = campaignRepository.save(campaign);

        billingService.recordCampaignExecution(company);

        return savedCampaign;
    }

    private List<Contact> findTargetContacts(ScheduleCampaignRequest request, Company company) {
        if (request.getContactIds() != null && !request.getContactIds().isEmpty()) {
            log.info("Buscando {} contatos individuais para campanha.", request.getContactIds().size());
            List<Contact> contacts = contactRepository.findAllById(request.getContactIds());
            // Filtrar para garantir que todos os contatos pertencem à empresa correta
            return contacts.stream()
                    .filter(c -> c.getCompany().getId().equals(company.getId()))
                    .collect(Collectors.toList());
        }
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            // Lógica mais complexa para buscar contatos por tags.
            // Requer um método customizado no ContactRepository.
            // Ex: contactRepository.findByCompanyAndTagsIn(company, request.getTagIds());
            log.warn("Busca de contatos por tags ainda não implementada.");
            // throw new UnsupportedOperationException("Busca de contatos por tags ainda não implementada.");
            return new ArrayList<>(); // Retorna vazio por enquanto
        }
        throw new BusinessException("É necessário especificar 'tagIds' ou 'contactIds' para a campanha.");
    }
    
    @Override
    public Page<ScheduledCampaign> listCampaignsByCompany(Company company, Pageable pageable) {
        
        if (company == null) {
            throw new BusinessException("Empresa não fornecida para listar campanhas.");
        }
        // Adicionar método ao repository
        return campaignRepository.findByCompanyOrderByCreatedAtDesc(company, pageable);
    }

    @Override
    public Page<ScheduledCampaign> listAllCampaigns(Pageable pageable) {
        // A verificação de role deve ser feita no controller antes de chamar este método.
        return campaignRepository.findAll(pageable);
    }

    @Override
    public Page<ScheduledCampaign> listCampaignsByCompanyId(Long companyId, Pageable pageable) {
        // A verificação de role deve ser feita no controller.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));
        return campaignRepository.findByCompanyOrderByCreatedAtDesc(company, pageable);
    }

    @Override
    public Optional<ScheduledCampaign> getCampaignDetails(Long campaignId, User user) {
        Optional<ScheduledCampaign> optCampaign = campaignRepository.findById(campaignId);
        if (optCampaign.isPresent()) {
            ScheduledCampaign campaign = optCampaign.get();
            // Checagem de permissão: ou é admin, ou pertence à empresa da campanha
            if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
                !campaign.getCompany().getId().equals(user.getCompany().getId())) {
                throw new AccessDeniedException("Você não tem permissão para ver esta campanha.");
            }
        }
        return optCampaign;
    }

    @Override
    @Transactional
    public ScheduledCampaign cancelCampaign(Long campaignId, User user) {
        ScheduledCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campaignId + " não encontrada."));

        // Checagem de permissão
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
            !campaign.getCompany().getId().equals(user.getCompany().getId())) {
            throw new AccessDeniedException("Você não tem permissão para cancelar esta campanha.");
        }

        // Apenas campanhas pendentes ou em processamento podem ser canceladas
        if (campaign.getStatus() != ScheduledCampaign.CampaignStatus.PENDING &&
            campaign.getStatus() != ScheduledCampaign.CampaignStatus.PROCESSING &&
            campaign.getStatus() != ScheduledCampaign.CampaignStatus.PAUSED) {
            throw new BusinessException("Apenas campanhas pendentes, pausadas ou em processamento podem ser canceladas. Status atual: " + campaign.getStatus());
        }

        // Ao cancelar, atualiza as mensagens pendentes para CANCELED também, para o scheduler não pegá-las.
        // Isso pode ser uma operação pesada se houver milhões de mensagens.
        messageRepository.updateStatusForPendingMessagesByCampaign(campaign.getId(), ScheduledMessage.MessageStatus.CANCELED);
        
        campaign.setStatus(ScheduledCampaign.CampaignStatus.CANCELED);
        ScheduledCampaign canceledCampaign = campaignRepository.save(campaign);
        callbackService.sendCampaignStatusCallback(canceledCampaign.getCompany().getId(), canceledCampaign.getId());
        return campaignRepository.save(campaign);
    }

    @Override
    @Transactional
    public ScheduledCampaign pauseCampaign(Long campaignId, User user) {
        ScheduledCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campaignId + " não encontrada."));

        // Checagem de permissão
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
            !campaign.getCompany().getId().equals(user.getCompany().getId())) {
            throw new AccessDeniedException("Você não tem permissão para pausar esta campanha.");
        }

        // Apenas campanhas pendentes ou em processamento podem ser pausadas
        if (campaign.getStatus() != ScheduledCampaign.CampaignStatus.PENDING &&
            campaign.getStatus() != ScheduledCampaign.CampaignStatus.PROCESSING) {
            throw new BusinessException("Apenas campanhas pendentes ou em processamento podem ser pausadas. Status atual: " + campaign.getStatus());
        }

        campaign.setStatus(ScheduledCampaign.CampaignStatus.PAUSED);
        ScheduledCampaign pausedCampaign = campaignRepository.save(campaign);
        callbackService.sendCampaignStatusCallback(pausedCampaign.getCompany().getId(), pausedCampaign.getId());
        return campaignRepository.save(campaign);
    }

    @Override
    @Transactional
    public ScheduledCampaign resumeCampaign(Long campaignId, User user) {
        ScheduledCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campaignId + " não encontrada."));

        // Checagem de permissão
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
            !campaign.getCompany().getId().equals(user.getCompany().getId())) {
            throw new AccessDeniedException("Você não tem permissão para retomar esta campanha.");
        }

        if (campaign.getStatus() != ScheduledCampaign.CampaignStatus.PAUSED) {
            throw new BusinessException("Apenas campanhas pausadas podem ser retomadas. Status atual: " + campaign.getStatus());
        }

        // Ao retomar, volta para PENDING para que o scheduler possa pegá-la novamente
        campaign.setStatus(ScheduledCampaign.CampaignStatus.PENDING);
        ScheduledCampaign resumedCampaign = campaignRepository.save(campaign);
        callbackService.sendCampaignStatusCallback(resumedCampaign.getCompany().getId(), resumedCampaign.getId());
        return campaignRepository.save(campaign);
    }

    @Override
    public Mono<Void> subscribeAppToWaba(Company company) {
        String wabaId = company.getMetaWabaId();
        if (wabaId == null || wabaId.isBlank()) {
            return Mono.error(new BusinessException("Empresa não tem um WABA ID configurado para assinar webhooks."));
        }

        String endpoint = "/" + wabaId + "/subscribed_apps";
        log.info("Assinando App do BSP à WABA ID {} para receber webhooks.", wabaId);

        // Usa o WebClient com o token do BSP
        WebClient bspWebClient = getBspWebClient(); // Assumindo que você tem este helper no serviço

        return bspWebClient.post().uri(endpoint).retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Erro da API Meta ao assinar App à WABA {}: Status={}, Body={}",
                                wabaId, clientResponse.statusCode(), errorBody);
                        return Mono.error(new BusinessException("Falha ao assinar App para webhooks: " + errorBody));
                    })
            )
            .bodyToMono(JsonNode.class)
            .flatMap(responseNode -> {
                if (responseNode.path("success").asBoolean(false)) {
                    log.info("App assinado com sucesso à WABA ID {}.", wabaId);
                    return Mono.empty(); // Retorna Mono<Void>
                } else {
                    log.error("API da Meta retornou 'success: false' ao assinar App à WABA {}. Resposta: {}", wabaId, responseNode);
                    return Mono.error(new BusinessException("API da Meta falhou ao assinar App."));
                }
            }).then();
    }

    private WebClient getBspWebClient() {
        if (bspSystemUserAccessToken == null || bspSystemUserAccessToken.isBlank()) {
            throw new BusinessException("Token de System User do BSP não configurado.");
        }
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }
}
