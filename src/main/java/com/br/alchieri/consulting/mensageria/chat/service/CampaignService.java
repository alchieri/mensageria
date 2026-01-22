package com.br.alchieri.consulting.mensageria.chat.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

import reactor.core.publisher.Mono;

public interface CampaignService {

    /**
     * Agenda uma nova campanha de envio em massa.
     * @param request DTO com os detalhes da campanha.
     * @param creator O usuário que está criando a campanha.
     * @return A entidade ScheduledCampaign criada.
     */
    ScheduledCampaign scheduleNewCampaign(ScheduleCampaignRequest request, User creator);

    /**
     * Lista as campanhas de uma empresa de forma paginada.
     * @param company A empresa dona das campanhas.
     * @param pageable Informações de paginação.
     * @return Uma página de ScheduledCampaign.
     */
    Page<ScheduledCampaign> listCampaignsByCompany(Company company, Pageable pageable);

    /** Lista TODAS as campanhas paginadas (Apenas para BSP Admin). */
    Page<ScheduledCampaign> listAllCampaigns(Pageable pageable);

    /** Busca campanhas por um companyId específico (Apenas para BSP Admin). */
    Page<ScheduledCampaign> listCampaignsByCompanyId(Long companyId, Pageable pageable);

    /**
     * Cancela uma campanha agendada.
     * @param campaignId ID da campanha a ser cancelada.
     * @param user O usuário que está tentando cancelar.
     * @return detalhes deuma campanha.
     */
    Optional<ScheduledCampaign> getCampaignDetails(Long campaignId, User user);

    /**
     * Cancela uma campanha agendada.
     * @param campaignId ID da campanha a ser cancelada.
     * @param user O usuário que está tentando cancelar.
     * @return A campanha atualizada para o estado CANCELED.
     */
    ScheduledCampaign cancelCampaign(Long campaignId, User user);

    /**
     * Cancela uma campanha agendada.
     * @param campaignId ID da campanha a ser cancelada.
     * @param user O usuário que está tentando cancelar.
     * @return A campanha atualizada para o estado PAUSED.
     */
    ScheduledCampaign pauseCampaign(Long campaignId, User user);

    /**
     * Cancela uma campanha agendada.
     * @param campaignId ID da campanha a ser cancelada.
     * @param user O usuário que está tentando cancelar.
     * @return A campanha.
     */
    ScheduledCampaign resumeCampaign(Long campaignId, User user);

    Mono<Void> subscribeAppToWaba(Company company);
}
