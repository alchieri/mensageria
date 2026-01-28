package com.br.alchieri.consulting.mensageria.model;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Filter;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "meta_business_managers")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@Data
@NoArgsConstructor
public class MetaBusinessManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @Column(name = "meta_business_id", nullable = false)
    private String metaBusinessId; // ID na Meta (ex: 123456789)

    private String name; // Nome do Business Manager

    // Um Business Manager tem vários Catálogos
    @OneToMany(mappedBy = "businessManager", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Catalog> catalogs = new ArrayList<>();
}
