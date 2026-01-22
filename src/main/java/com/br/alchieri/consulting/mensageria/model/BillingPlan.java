package com.br.alchieri.consulting.mensageria.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.model.enums.BillingType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_plans")
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BillingPlan {

    @Id
    // Usaremos o ID do Cliente como chave primária e chave estrangeira
    // para garantir um relacionamento OneToOne real.
    @Column(name = "company_id")
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false) // optional=false significa que um BillingPlan DEVE ter um Client
    @MapsId // Mapeia o ID desta entidade para o ID da entidade Client
    @JoinColumn(name = "company_id") // Chave estrangeira para a tabela clients
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingType billingType = BillingType.MONTHLY; // Tipo de cobrança padrão

    @Column(nullable = false, precision = 10, scale = 2) // Ex: 99999999.99
    private BigDecimal monthlyFee; // Valor da mensalidade

    // --- MENSAGENS
    @Column(nullable = false)
    private Integer monthlyMessageLimit; // Quantidade de mensagens incluídas por mês

    @Column(nullable = false)
    private Integer currentMonthMessagesSent = 0;

    @Column(nullable = false, precision = 19, scale = 8)
    @Schema(description = "Sua taxa de plataforma fixa por mensagem enviada.", example = "0.008")
    private BigDecimal platformFeePerMessage; // Sua taxa (ex: R$ 0,008)

    @Column(nullable = false, precision = 5, scale = 2)
    @Schema(description = "Margem de lucro percentual sobre o custo da Meta (ex: 20.00 para 20%).", example = "20.00")
    private BigDecimal metaCostMarkupPercentage; // Sua margem sobre o custo da Meta

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentMonthMetaCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentMonthPlatformFee = BigDecimal.ZERO;

    @Column(nullable = true) // Pode não haver limite diário
    private Integer dailyMessageLimit;   // Quantidade de mensagens incluídas por dia

    @Column(nullable = false)
    private Integer currentDayMessagesSent = 0;

    // --- TEMPLATES
    @Column(nullable = false)
    private Integer activeTemplateLimit; // Limite de templates ATIVOS

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerExceededActiveTemplate; // Preço por template ativo excedente

    // --- FLOWS
    @Column(nullable = false)
    private Integer activeFlowLimit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerExceededActiveFlow;

    // --- CAMPANHAS (Nova Estrutura) ---
    @Column(nullable = false)
    private Integer monthlyCampaignLimit; // Limite de campanhas executadas por mês

    @Column(nullable = false)
    private Integer currentMonthCampaignsExecuted = 0; // Contador de uso

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerExceededCampaign;
    
    @Column // Data da última redefinição dos contadores mensais
    private LocalDateTime lastMonthlyReset;

    @Column // Data da última redefinição dos contadores diários
    private LocalDateTime lastDailyReset;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
