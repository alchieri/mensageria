package com.br.alchieri.consulting.mensageria.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.response.CompanyUsageResponse;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.dto.request.CreateBillingPlanRequest;
import com.br.alchieri.consulting.mensageria.model.BillingPlan;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface BillingService {

    // --- Métodos de Gerenciamento (Admin) ---

    /** Cria ou atualiza o plano de cobrança para uma empresa. */
    BillingPlan createOrUpdateCompanyBillingPlan(CreateBillingPlanRequest request);

    /** Busca o plano de cobrança por ID da empresa. */
    Optional<BillingPlan> getBillingPlanByCompanyId(Long companyId);

    // --- Métodos de Consulta (Cliente) ---

    /** Obtém o uso atual e os custos estimados para uma empresa. */
    Optional<CompanyUsageResponse> getClientCompanyUsage(Company company);

    // --- Métodos de Verificação de Limites (Internos) ---

    /** Verifica se a empresa pode enviar uma determinada quantidade de mensagens. */
    boolean canCompanySendMessages(Company company, int messageCount);

    /** Verifica se a empresa pode criar/publicar um novo template ativo. */
    boolean canCompanyCreateTemplate(Company company);

    /** Verifica se a empresa pode criar/publicar um novo Flow ativo. */
    boolean canCompanyCreateFlow(Company company);

    /** Verifica se a empresa pode executar uma nova campanha. */
    boolean canCompanyExecuteCampaign(Company company);

    // --- Métodos de Registro de Uso (Internos) ---

    /** Registra o envio de mensagens nos contadores. */
    void recordMessagesSent(Company company, int messageCount);

    /** Registra a execução de uma campanha no contador. */
    void recordCampaignExecution(Company company);

    /** Registra os custos de uma mensagem (Meta + Plataforma) nos contadores. */
    void recordCosts(Company company, BigDecimal metaCost, BigDecimal platformFee);

    // --- Métodos de Cálculo (Internos) ---

    /** Calcula o custo da Meta para uma mensagem específica. */
    BigDecimal calculateMetaCostForMessage(WhatsAppMessageLog messageLog);

    /** Calcula a taxa da plataforma para uma mensagem específica. */
    BigDecimal calculatePlatformFee(WhatsAppMessageLog messageLog);

    // --- Métodos de Manutenção (Scheduler) ---

    /** Reseta os contadores diários e mensais conforme a data. */
    void resetUsageCounters();

    Page<BillingPlan> findAllBillingPlans(Pageable pageable);

    // --- NOVOS MÉTODOS PARA O SCHEDULER ---
    
    /** Gera as faturas para todas as empresas para o mês anterior. */
    void generateMonthlyInvoices();
    
    /** Reseta apenas os contadores diários. */
    void resetDailyUsageCounters();
}
