package com.br.alchieri.consulting.mensageria.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_name", columnList = "name", unique = true),
    @Index(name = "idx_company_document_number", columnList = "documentNumber", unique = true), // Se o documento for único
    @Index(name = "idx_company_meta_waba_id", columnList = "metaWabaId", unique = true)
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotBlank(message = "Nome da empresa é obrigatório.")
    @Size(max = 255)
    @Column(nullable = false, unique = true)
    private String name; // Nome da empresa cliente

    // @Column(nullable = true, unique = true) // Pode ser nulo se ainda não configurado, mas único quando preenchido
    // private String metaWabaId;

    // @Column(nullable = true) // O principal Phone Number ID da empresa, pode haver outros
    // @Deprecated
    // private String metaPrimaryPhoneNumberId;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsAppPhoneNumber> phoneNumbers = new ArrayList<>();

    // @Column(nullable = true)
    // @Deprecated
    // private String facebookBusinessManagerId;

    @Size(max = 20)
    @Column(length = 20, unique = true, nullable = true) // CNPJ/CPF, único se preenchido
    private String documentNumber; // CNPJ/CPF

    @Embedded
    private Address address;

    @Email(message = "Formato de email de contato inválido.")
    @Size(max = 150)
    @Column(length = 150, nullable = true)
    private String contactEmail;

    @Size(max = 20)
    @Pattern(regexp = "^\\+?[0-9. ()-]{7,20}$", message = "Formato de celular de contato inválido")
    @Column(length = 20, nullable = true)
    private String contactPhoneNumber;

    // Relacionamento com Usuários (Uma empresa pode ter muitos usuários)
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();

    // Relacionamento com o Plano de Cobrança (Uma empresa tem um plano)
    @OneToOne(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private BillingPlan billingPlan;

    // Relacionamento com Templates (Uma empresa pode ter muitos templates)
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ClientTemplate> clientTemplates = new ArrayList<>();

    // Relacionamento com Logs de Mensagens (Uma empresa tem muitos logs)
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WhatsAppMessageLog> messageLogs = new ArrayList<>();


    @Enumerated(EnumType.STRING)
    private OnboardingStatus onboardingStatus;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = true)
    private String generalCallbackUrl; // Callback geral da empresa

    @Column(nullable = true)
    private String templateStatusCallbackUrl; // Callback específico para status de template da empresa

    @Column(unique = true, nullable = true)
    private String metaFlowPublicKeyId;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL)
    private List<MetaBusinessManager> businessManagers = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Métodos helper para adicionar/remover usuários, templates, etc.
    public void addUser(User user) {
        if (user != null) {
            this.users.add(user);
            user.setCompany(this);
        }
    }

    public void removeUser(User user) {
        if (user != null) {
            this.users.remove(user);
            user.setCompany(null);
        }
    }

    public void addClientTemplate(ClientTemplate template) {
        if (template != null) {
            this.clientTemplates.add(template);
            template.setCompany(this);
        }
    }

    public void removeClientTemplate(ClientTemplate template) {
        if (template != null) {
            this.clientTemplates.remove(template);
            template.setCompany(null);
        }
    }

    public void addMessageLog(WhatsAppMessageLog log) {
        if (log != null) {
            this.messageLogs.add(log);
            log.setCompany(this);
        }
    }

    public void setBillingPlanDetails(BillingPlan plan) {
        if (plan != null) {
            plan.setCompany(this);
        }
        this.billingPlan = plan;
    }
}
