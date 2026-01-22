package com.br.alchieri.consulting.mensageria.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meta_rate_cards",
    // Adiciona uma constraint UNIQUE para a combinação de mercado, categoria, data e início do tier
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rate_card_unique_rate",
                          columnNames = {"marketName", "category", "effectiveDate", "volumeTierStart"})
    },
    indexes = {
        @Index(name = "idx_rate_card_lookup", columnList = "countryCode, category, effectiveDate, volumeTierStart")
    }
)
@Data
@NoArgsConstructor
public class MetaRateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String countryCode;

    @Column(nullable = false)
    private String marketName; // Ex: "Brasil"

    @Column(nullable = false)
    private String currency; // Ex: "USD", "BRL"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateCategory category; // MARKETING, UTILITY, AUTHENTICATION

    @Column(nullable = false)
    private Long volumeTierStart = 0L; // Início da faixa de volume (ex: 0, 250001)

    @Column(nullable = true) // Pode ser nulo para o último tier
    private Long volumeTierEnd;   // Fim da faixa de volume (ex: 250000)

    @Column(nullable = false, precision = 19, scale = 8) // Alta precisão para tarifas
    private BigDecimal rate; // A tarifa da Meta para esta faixa

    @Column(nullable = false)
    private LocalDate effectiveDate; // Data a partir da qual esta tarifa é válida
}
