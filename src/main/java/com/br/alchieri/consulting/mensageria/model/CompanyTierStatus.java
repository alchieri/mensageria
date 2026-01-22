package com.br.alchieri.consulting.mensageria.model;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.hibernate.annotations.UpdateTimestamp;

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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_tier_statuses", indexes = {
        @Index(name = "uk_tier_status", columnList = "wabaId, category, effectiveMonth", unique = true)
})
@Data
@NoArgsConstructor
public class CompanyTierStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String wabaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateCategory category;

    @Column(nullable = false)
    private YearMonth effectiveMonth;

    @Column(nullable = false)
    private String currentTier; // Ex: "250001:750000"

    @Column(nullable = false)
    private String region;

    @UpdateTimestamp
    private LocalDateTime lastUpdatedAt;
}
