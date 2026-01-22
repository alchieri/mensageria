package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.chat.model.enums.ContactStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.LeadSource;
import com.br.alchieri.consulting.mensageria.model.Address;
import com.br.alchieri.consulting.mensageria.model.Company;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contacts", indexes = {
        // Índice para buscar contatos de uma empresa rapidamente
        @Index(name = "idx_contact_company", columnList = "company_id"),
        @Index(name = "idx_contact_status", columnList = "status"), // Índice para filtrar por status
        // Constraint única para evitar contatos duplicados (mesmo número) para a mesma empresa
        @Index(name = "uk_contact_company_phone", columnList = "company_id, phoneNumber", unique = true)
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // Empresa dona do contato

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String phoneNumber; // Número no formato E.164 (ex: 5511999998888)

    @Email
    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Gender gender;

    // --- Informações Profissionais ---
    @Column(length = 100)
    private String companyName; // Nome da empresa ONDE o contato trabalha

    @Column(length = 100)
    private String jobTitle; // Cargo

    @Column(length = 100)
    private String department; // Departamento

    // --- Endereço (usando classe embutida) ---
    @Embedded
    private Address address;

    // --- Preferências e Status ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactStatus status = ContactStatus.ACTIVE; // Padrão para Ativo

    @Column(length = 10) // Ex: "pt-BR", "en-US"
    private String preferredLanguage;

    @Column(length = 50) // Ex: "America/Sao_Paulo", "UTC"
    private String timeZone;

    @Column(nullable = false)
    private boolean isVip = false; // Contato VIP

    @Column(nullable = false)
    private boolean allowMarketingMessages = true; // Padrão permite

    @Column(nullable = false)
    private boolean allowNotifications = true; // Padrão permite


    // --- Dados de CRM/Marketing ---
    @Enumerated(EnumType.STRING)
    private LeadSource leadSource; // Origem do Lead

    @Column
    private Integer leadScore = 0; // Pontuação do lead


    // --- Outros ---
    @Column(columnDefinition = "TEXT")
    private String notes; // Observações

    // --- Tags ---
    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
        name = "contact_tags", // Nome da tabela de junção
        joinColumns = @JoinColumn(name = "contact_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(nullable = false)
    @ColumnDefault("0") // Define o valor padrão no DDL
    private Integer unreadMessagesCount = 0; // Contador de mensagens não lidas

    @Type(JsonType.class) // Usa o tipo JSON do Hypersistence
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customFields; // Mapeia para um JSONB no PostgreSQL

    // --- Timestamps ---
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Gender {
        MASCULINO, FEMININO, OUTRO, PREFIRO_NAO_INFORMAR
    }
}
