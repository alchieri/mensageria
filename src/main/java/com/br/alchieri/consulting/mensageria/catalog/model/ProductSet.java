package com.br.alchieri.consulting.mensageria.catalog.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
@Table(name = "product_sets")
@Data
@NoArgsConstructor
public class ProductSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    @JsonIgnore
    private Catalog catalog;

    @Column(nullable = false)
    private String name;

    @Column(name = "meta_product_set_id", unique = true, nullable = false)
    private String metaProductSetId; // O ID importante para o WhatsApp

    // Armazena o filtro JSON usado (ex: {"retailer_id": {"is_any": ["123", "456"]}})
    @Column(columnDefinition = "TEXT")
    private String filterDefinition; 

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
