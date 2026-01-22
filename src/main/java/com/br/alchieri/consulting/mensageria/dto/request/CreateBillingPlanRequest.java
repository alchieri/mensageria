package com.br.alchieri.consulting.mensageria.dto.request;

import java.math.BigDecimal;

import com.br.alchieri.consulting.mensageria.model.enums.BillingType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Dados para criar ou atualizar um plano de cobrança para uma empresa cliente.")
public class CreateBillingPlanRequest {

    @NotNull(message = "O ID da empresa é obrigatório.")
    @Schema(description = "ID da empresa para a qual o plano será aplicado.", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long companyId;

    @NotNull(message = "O tipo de cobrança é obrigatório.")
    @Schema(description = "Tipo de plano.", defaultValue = "MONTHLY", example = "MONTHLY")
    private BillingType billingType = BillingType.MONTHLY;

    @NotNull(message = "A mensalidade é obrigatória.")
    @DecimalMin(value = "0.0", inclusive = true, message = "A mensalidade não pode ser negativa.")
    @Schema(description = "Valor fixo da mensalidade (assinatura da plataforma).", example = "99.90", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal monthlyFee;

    // --- MENSAGENS ---
    @NotNull(message = "O limite mensal de mensagens é obrigatório.")
    @Min(value = 0, message = "O limite de mensagens não pode ser negativo.")
    @Schema(description = "Quantidade de mensagens incluídas no plano mensal.", example = "10000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer monthlyMessageLimit;

    @Min(value = 0, message = "O limite diário de mensagens não pode ser negativo.")
    @Schema(description = "Limite diário de mensagens (opcional, nulo para ilimitado).", example = "500")
    private Integer dailyMessageLimit;

    @NotNull(message = "A taxa de plataforma por mensagem é obrigatória.")
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Sua taxa de serviço fixa por cada mensagem enviada (seja ela cobrada pela Meta ou não).",
            example = "0.008", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal platformFeePerMessage;

    @NotNull(message = "A margem de lucro sobre o custo da Meta é obrigatória.")
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Margem de lucro percentual a ser adicionada sobre o custo da Meta para cada mensagem cobrável (Ex: 20.0 para 20%).",
            example = "20.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal metaCostMarkupPercentage;

    // --- TEMPLATES ---
    @NotNull(message = "O limite de templates ativos é obrigatório.")
    @Min(value = 0, message = "O limite de templates não pode ser negativo.")
    @Schema(description = "Quantidade de templates que podem estar ativos simultaneamente.", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer activeTemplateLimit;

    @NotNull(message = "O preço por template ativo excedente é obrigatório.")
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Valor cobrado mensalmente por cada template ativo que exceda o limite.", example = "5.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal pricePerExceededActiveTemplate;

    // --- FLOWS ---
    @NotNull(message = "O limite de Flows ativos é obrigatório.")
    @Min(value = 0, message = "O limite de Flows não pode ser negativo.")
    @Schema(description = "Quantidade de Flows que podem estar ativos (publicados) simultaneamente.", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer activeFlowLimit;

    @NotNull(message = "O preço por Flow ativo excedente é obrigatório.")
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Valor cobrado mensalmente por cada Flow ativo que exceda o limite.", example = "10.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal pricePerExceededActiveFlow;

    // --- CAMPANHAS ---
    @NotNull(message = "O limite mensal de campanhas é obrigatório.")
    @Min(value = 0, message = "O limite de campanhas não pode ser negativo.")
    @Schema(description = "Quantidade de campanhas que podem ser executadas por mês.", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer monthlyCampaignLimit;

    @NotNull(message = "O preço por campanha excedente é obrigatório.")
    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Valor cobrado por cada campanha executada que exceda o limite mensal.", example = "2.50", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal pricePerExceededCampaign;
}
