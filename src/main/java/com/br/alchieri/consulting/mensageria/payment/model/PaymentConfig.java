package com.br.alchieri.consulting.mensageria.payment.model;

import org.hibernate.annotations.Filter;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "payment_configs")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@Data
public class PaymentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider; // Define qual estratégia usar

    @Column(name = "access_token", nullable = false)
    private String accessToken; // MP Access Token / Asaas API Key

    @Column(name = "public_key")
    private String publicKey; // MP Public Key (se necessário)

    @Column(name = "is_active")
    private boolean active = true;
    
    // Webhook secret para validar assinaturas
    @Column(name = "webhook_secret")
    private String webhookSecret;
}
