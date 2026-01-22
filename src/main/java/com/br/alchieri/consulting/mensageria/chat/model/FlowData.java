package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import com.br.alchieri.consulting.mensageria.model.Company;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flow_data", indexes = {
        @Index(name = "idx_flowdata_company_receivedat", columnList = "company_id, receivedAt DESC"),
        @Index(name = "idx_flowdata_sender_wa_id", columnList = "senderWaId")
})
@Data
@NoArgsConstructor
public class FlowData {

     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = true)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = true)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = true)
    private Flow flow;

    @Column(nullable = false)
    private String senderWaId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String decryptedJsonResponse; // O JSON com os dados do formulário

    @Column
    private LocalDateTime lastCallbackAttempt;

    @Column
    private String lastCallbackStatus; // Ex: "PENDING", "SUCCESS", "FAILED_FINAL"

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime receivedAt;
}
