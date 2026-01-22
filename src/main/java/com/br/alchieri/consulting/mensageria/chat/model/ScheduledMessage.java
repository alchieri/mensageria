package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scheduled_messages", indexes = {
        @Index(name = "idx_sm_status_scheduled_at", columnList = "status, scheduledAt")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScheduledMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private ScheduledCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact; // O contato de destino

    @Column(nullable = false)
    private LocalDateTime scheduledAt; // Hora exata para esta mensagem (herdada da campanha)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status = MessageStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String templateParametersJson; // JSON com os parâmetros dinâmicos para este contato

    @Column(nullable = true)
    private String wamid; // WAMID retornado pela Meta, para rastreamento futuro

    @Column(columnDefinition = "TEXT")
    private String failureReason; // Se o envio falhar

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum MessageStatus {
        PENDING,    // Aguardando o scheduler
        QUEUED,     // Colocada na fila SQS
        SENT,       // Status final de sucesso (após webhook da Meta)
        DELIVERED,  // Status final de sucesso (após webhook da Meta)
        READ,       // Status final de sucesso (após webhook da Meta)
        FAILED,     // Falha no envio (após webhook da Meta)
        CANCELED
    }
}
