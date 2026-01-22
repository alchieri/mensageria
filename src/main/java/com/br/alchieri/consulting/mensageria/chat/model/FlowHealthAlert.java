package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flow_health_alerts")
@Data
@NoArgsConstructor
public class FlowHealthAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id") // Link para o nosso registro de Flow
    private Flow flow;

    @Column(nullable = false)
    private String metaFlowId; // ID do Flow na Meta

    @Column(nullable = false)
    private String eventType; // Ex: FLOW_STATUS_CHANGE, ENDPOINT_ERROR_RATE

    @Column(columnDefinition = "TEXT")
    private String message; // Mensagem descritiva do webhook

    @Column
    private String alertState; // ACTIVATED, DEACTIVATED

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String eventDataJson; // O objeto 'value' completo do webhook

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime receivedAt;
}
