package com.br.alchieri.consulting.mensageria.dto.response;

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
@Schema(description = "Detalhes da configuração do plano de cobrança de uma empresa cliente.")
public class BillingPlanResponse {

    @Schema(description = "ID da Empresa à qual este plano pertence.")
    private Long companyId;

    @Schema(description = "Tipo de plano de cobrança.")
    private BillingType billingType;

    @Schema(description = "Valor da mensalidade do plano.", example = "99.90")
    private BigDecimal monthlyFee;

    // --- Configuração de Mensagens ---
    @Schema(description = "Limite mensal de mensagens incluídas no plano.")
    private Integer monthlyMessageLimit;
    @Schema(description = "Limite diário de mensagens (se aplicável).")
    private Integer dailyMessageLimit;
    @Schema(description = "Sua taxa de serviço fixa por cada mensagem enviada.")
    private BigDecimal platformFeePerMessage;
    @Schema(description = "Margem de lucro percentual sobre o custo da Meta.")
    private BigDecimal metaCostMarkupPercentage;

    // --- Configuração de Templates ---
    @Schema(description = "Limite de templates ativos incluídos no plano.")
    private Integer activeTemplateLimit;
    @Schema(description = "Valor cobrado mensalmente por cada template ativo excedente.")
    private BigDecimal pricePerExceededActiveTemplate;

    // --- Configuração de Flows ---
    @Schema(description = "Limite de Flows ativos incluídos no plano.")
    private Integer activeFlowLimit;
    @Schema(description = "Valor cobrado mensalmente por cada Flow ativo excedente.")
    private BigDecimal pricePerExceededActiveFlow;

    // --- Configuração de Campanhas ---
    @Schema(description = "Limite de campanhas que podem ser executadas por mês.")
    private Integer monthlyCampaignLimit;
    @Schema(description = "Valor cobrado por cada campanha executada que exceda o limite mensal.")
    private BigDecimal pricePerExceededCampaign;

    // --- Dados de Uso (também úteis para o admin) ---
    @Schema(description = "Total de mensagens enviadas no mês corrente.")
    private Integer currentMonthMessagesSent;
    @Schema(description = "Total de campanhas executadas no mês corrente.")
    private Integer currentMonthCampaignsExecuted;
    @Schema(description = "Custo total repassado da Meta no mês corrente.")
    private BigDecimal currentMonthMetaCost;
    @Schema(description = "Total da sua taxa de plataforma no mês corrente.")
    private BigDecimal currentMonthPlatformFee;

    @Schema(description = "Data de criação do plano.")
    private LocalDateTime createdAt;
    @Schema(description = "Data da última atualização do plano.")
    private LocalDateTime updatedAt;

    public static BillingPlanResponse fromEntity(BillingPlan entity) {
        if (entity == null) {
            return null;
        }
        return BillingPlanResponse.builder()
                .companyId(entity.getCompany().getId())
                .billingType(entity.getBillingType())
                .monthlyFee(entity.getMonthlyFee())
                .monthlyMessageLimit(entity.getMonthlyMessageLimit())
                .dailyMessageLimit(entity.getDailyMessageLimit())
                .platformFeePerMessage(entity.getPlatformFeePerMessage())
                .metaCostMarkupPercentage(entity.getMetaCostMarkupPercentage())
                .activeTemplateLimit(entity.getActiveTemplateLimit())
                .pricePerExceededActiveTemplate(entity.getPricePerExceededActiveTemplate())
                .activeFlowLimit(entity.getActiveFlowLimit())
                .pricePerExceededActiveFlow(entity.getPricePerExceededActiveFlow())
                .monthlyCampaignLimit(entity.getMonthlyCampaignLimit())
                .pricePerExceededCampaign(entity.getPricePerExceededCampaign())
                .currentMonthMessagesSent(entity.getCurrentMonthMessagesSent())
                .currentMonthCampaignsExecuted(entity.getCurrentMonthCampaignsExecuted())
                .currentMonthMetaCost(entity.getCurrentMonthMetaCost())
                .currentMonthPlatformFee(entity.getCurrentMonthPlatformFee())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
