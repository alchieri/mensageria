package com.br.alchieri.consulting.mensageria.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "api_keys")
@Data
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name; // Ex: "Integração CRM Salesforce"

    @Column(nullable = false, length = 10)
    private String keyPrefix; // Primeiros 8 chars para identificar visualmente (ex: "sk_live_12...")

    @Column(nullable = false, unique = true)
    private String keyHash; // Hash SHA-256 da chave real. A chave real NUNCA é salva.

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private LocalDateTime expiresAt; // Pode ser nulo para chaves eternas

    @Column
    private LocalDateTime lastUsedAt; // Auditoria de uso

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
