package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.model.BillingPlan;
import com.br.alchieri.consulting.mensageria.model.enums.BillingType;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalhes do uso atual do plano de cobrança da empresa e custos estimados.")
public class CompanyUsageResponse {

    @Schema(description = "ID da Empresa.")
    private Long companyId;

    @Schema(description = "Tipo de plano de cobrança.")
    private BillingType billingType;

    // --- Resumo Financeiro do Mês Corrente ---
    @Schema(description = "Valor fixo da mensalidade do plano.")
    private BigDecimal monthlyFee;
    @Schema(description = "Custo total estimado repassado da Meta no mês corrente.")
    private BigDecimal currentMonthMetaCost;
    @Schema(description = "Total estimado da sua taxa de plataforma no mês corrente.")
    private BigDecimal currentMonthPlatformFee;
    @Schema(description = "Custo estimado para templates ativos excedentes (cobrado mensalmente).")
    private BigDecimal estimatedExceededActiveTemplateCost;
    @Schema(description = "Custo estimado para Flows ativos excedentes (cobrado mensalmente).")
    private BigDecimal estimatedExceededActiveFlowCost;
    @Schema(description = "Custo estimado para campanhas excedentes no mês corrente.")
    private BigDecimal estimatedExceededCampaignCost;
    @Schema(description = "Custo total estimado para o mês corrente (soma de todos os custos).")
    private BigDecimal estimatedTotalCost;

    // --- Uso de Mensagens ---
    @Schema(description = "Limite mensal de mensagens incluídas no plano.")
    private Integer monthlyMessageLimit;
    @Schema(description = "Total de mensagens enviadas no mês corrente.")
    private Integer currentMonthMessagesSent;
    @Schema(description = "Mensagens restantes no limite mensal.")
    private Integer remainingMonthlyMessages;
    @Schema(description = "Limite diário de mensagens (se aplicável).")
    private Integer dailyMessageLimit;
    @Schema(description = "Total de mensagens enviadas no dia corrente.")
    private Integer currentDayMessagesSent;
    @Schema(description = "Mensagens restantes no limite diário (se aplicável).")
    private Integer remainingDailyMessages;

    // --- Uso de Templates Ativos ---
    @Schema(description = "Limite de templates ativos incluídos no plano.")
    private Integer activeTemplateLimit;
    @Schema(description = "Total de templates atualmente ativos para a empresa.")
    private Integer currentActiveTemplates;

    // --- Uso de Flows Ativos ---
    @Schema(description = "Limite de Flows ativos incluídos no plano.")
    private Integer activeFlowLimit;
    @Schema(description = "Total de Flows atualmente ativos para a empresa.")
    private Integer currentActiveFlows;

    // --- Uso de Campanhas ---
    @Schema(description = "Limite de campanhas executadas no mês.")
    private Integer monthlyCampaignLimit;
    @Schema(description = "Total de campanhas executadas no mês corrente.")
    private Integer currentMonthCampaignsExecuted;
    @Schema(description = "Campanhas restantes no limite mensal.")
    private Integer remainingMonthlyCampaigns;

    @Schema(description = "Data e hora da última redefinição dos contadores mensais.")
    private LocalDateTime lastMonthlyReset;

    public record UsageCounts(int activeTemplates, int activeFlows, int executedCampaigns,
                               int currentDayMessages, int currentMonthMessages,
                               BigDecimal metaCost, BigDecimal platformFee) {}

    public static CompanyUsageResponse fromBillingPlan(BillingPlan plan, UsageCounts counts) {
        
        if (plan == null) return null;

        // --- Cálculos de Mensagens ---
        int remainingMonthlyMsg = Math.max(0, plan.getMonthlyMessageLimit() - counts.currentMonthMessages());
        Integer remainingDailyMsg = (plan.getDailyMessageLimit() != null) ?
                Math.max(0, plan.getDailyMessageLimit() - counts.currentDayMessages()) : null;

        // --- Cálculos de Templates Ativos ---
        int exceededTemplates = Math.max(0, counts.activeTemplates() - plan.getActiveTemplateLimit());
        BigDecimal exceededTemplateCost = plan.getPricePerExceededActiveTemplate().multiply(new BigDecimal(exceededTemplates));

        // --- Cálculos de Flows Ativos ---
        int exceededFlows = Math.max(0, counts.activeFlows() - plan.getActiveFlowLimit());
        BigDecimal exceededFlowCost = plan.getPricePerExceededActiveFlow().multiply(new BigDecimal(exceededFlows));
        
        // --- Cálculos de Campanhas ---
        int remainingCampaigns = Math.max(0, plan.getMonthlyCampaignLimit() - counts.executedCampaigns());
        int exceededCampaigns = Math.max(0, counts.executedCampaigns() - plan.getMonthlyCampaignLimit());
        BigDecimal exceededCampaignCost = plan.getPricePerExceededCampaign().multiply(new BigDecimal(exceededCampaigns));
        
        // --- Custo Total Estimado ---
        BigDecimal totalCost = plan.getMonthlyFee()
                                   .add(counts.metaCost())
                                   .add(counts.platformFee())
                                   .add(exceededTemplateCost)
                                   .add(exceededFlowCost)
                                   .add(exceededCampaignCost);

        return CompanyUsageResponse.builder()
                .companyId(plan.getCompany().getId())
                .billingType(plan.getBillingType())
                // Financeiro
                .monthlyFee(plan.getMonthlyFee())
                .currentMonthMetaCost(counts.metaCost())
                .currentMonthPlatformFee(counts.platformFee())
                .estimatedExceededActiveTemplateCost(exceededTemplateCost)
                .estimatedExceededActiveFlowCost(exceededFlowCost)
                .estimatedExceededCampaignCost(exceededCampaignCost)
                .estimatedTotalCost(totalCost)
                // Mensagens
                .monthlyMessageLimit(plan.getMonthlyMessageLimit())
                .currentMonthMessagesSent(counts.currentMonthMessages())
                .remainingMonthlyMessages(remainingMonthlyMsg)
                .dailyMessageLimit(plan.getDailyMessageLimit())
                .currentDayMessagesSent(counts.currentDayMessages())
                .remainingDailyMessages(remainingDailyMsg)
                // Templates
                .activeTemplateLimit(plan.getActiveTemplateLimit())
                .currentActiveTemplates(counts.activeTemplates())
                // Flows
                .activeFlowLimit(plan.getActiveFlowLimit())
                .currentActiveFlows(counts.activeFlows())
                // Campanhas
                .monthlyCampaignLimit(plan.getMonthlyCampaignLimit())
                .currentMonthCampaignsExecuted(counts.executedCampaigns())
                .remainingMonthlyCampaigns(remainingCampaigns)
                // Timestamps
                .lastMonthlyReset(plan.getLastMonthlyReset())
                .build();
    }
}
