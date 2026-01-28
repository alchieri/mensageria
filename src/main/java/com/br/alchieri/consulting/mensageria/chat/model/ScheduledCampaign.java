package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scheduled_campaigns")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScheduledCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User createdByUser;

    @Column(nullable = false)
    private String campaignName;

    // Detalhes da mensagem a ser enviada
    @Column(nullable = false)
    private String templateName; // Qual template usar
    @Column(nullable = false)
    private String languageCode; // Código do idioma
    // Os parâmetros do template podem ser dinâmicos por contato,
    // então não os armazenamos aqui, mas na ScheduledMessage.

    @Column(nullable = false)
    private LocalDateTime scheduledAt; // Quando a campanha deve começar a ser enviada

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.PENDING;

    // Opcional: Estatísticas da campanha
    private Integer totalMessages = 0;
    private Integer sentMessages = 0;
    private Integer failedMessages = 0;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ScheduledMessage> messages = new ArrayList<>();

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String componentMappingsJson; // Armazena o JSON da lista de TemplateComponentMapping

    @Column // Timestamp da última tentativa de callback de status da campanha
    private LocalDateTime lastCallbackAttempt;

    @Column // Status da última tentativa de callback
    private String lastCallbackStatus; // Ex: "SUCCESS", "FAILED_FINAL"

    public enum CampaignStatus {
        PENDING,    // Agendada, mas ainda não processada pelo scheduler
        PROCESSING, // O scheduler está pegando as mensagens desta campanha
        COMPLETED,  // Todas as mensagens foram enfileiradas
        PAUSED,     // Pausada por um administrador
        CANCELED    // Cancelada antes do envio
    }
}
