package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.model.Company;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flows", indexes = {
        @Index(name = "idx_flow_company", columnList = "company_id"),
        @Index(name = "idx_flow_meta_id", columnList = "metaFlowId", unique = true)
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Column(nullable = false)
    private String name; // Nome amigável do Flow no seu sistema

    @Column(unique = true)
    private String metaFlowId; // O ID retornado pela API da Meta

    @Column(columnDefinition = "TEXT", nullable = true)
    private String categoriesJson; // Armazena o JSON array de categorias

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String draftJsonDefinition; // O JSON que está sendo editado

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String publishedJsonDefinition; // O último JSON que foi publicado com sucesso

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String validationErrors; // Erros de validação retornados pela Meta

    @Column(nullable = false)
    private boolean hasUnpublishedChanges = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowStatus status = FlowStatus.DRAFT;

    @Column(nullable = false)
    private String jsonVersion; // Ex: "5.0", "7.3"

    @Column(nullable = true) // Apenas para Flows com endpoint
    private String dataApiVersion; // Ex: "3.0"
    
    // O endpoint URI agora pode ser armazenado aqui
    @Column(nullable = true)
    private String endpointUri;

    @Column
    private LocalDateTime lastCallbackAttempt;

    @Column
    private String lastCallbackStatus;

    @OneToMany(
        mappedBy = "flow", // "flow" é o nome do campo na entidade FlowHealthAlert
        cascade = CascadeType.ALL, // ALL inclui PERSIST, MERGE, REMOVE, etc.
        orphanRemoval = true,      // <<< CRUCIAL: Remove órfãos quando desassociados
        fetch = FetchType.LAZY
    )
    private List<FlowHealthAlert> healthAlerts = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void addHealthAlert(FlowHealthAlert alert) {
        healthAlerts.add(alert);
        alert.setFlow(this);
    }
    public void removeHealthAlert(FlowHealthAlert alert) {
        healthAlerts.remove(alert);
        alert.setFlow(null);
    }
}
