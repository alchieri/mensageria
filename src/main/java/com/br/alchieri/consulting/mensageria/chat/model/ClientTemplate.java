package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.model.Company;

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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "client_templates", indexes = {
        @Index(name = "idx_ct_company_name_lang", columnList = "company_id, templateName, language", unique = true)
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClientTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String templateName; // Nome do template (ex: "pedido_confirmado_v1")

    @Column(nullable = false)
    private String language; // Código do idioma (ex: "pt_BR")

    @Column(nullable = false)
    private String category; // MARKETING, UTILITY, AUTHENTICATION

    @Column(columnDefinition = "TEXT")
    private String componentsJson; // O JSON dos componentes submetidos

    @Column // ID do template retornado pela Meta após submissão (pode ser útil)
    private String metaTemplateId;

    @Column(nullable = false)
    private String status; // PENDING_SUBMISSION, SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, PAUSED, DISABLED

    @Column(columnDefinition = "TEXT")
    private String reason; // Motivo da rejeição ou outra informação da Meta

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
