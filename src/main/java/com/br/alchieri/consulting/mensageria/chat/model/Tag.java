package com.br.alchieri.consulting.mensageria.chat.model;

import java.util.HashSet;
import java.util.Set;

import com.br.alchieri.consulting.mensageria.model.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags", indexes = {
        // Garante que o nome da tag seja único para cada empresa
        @Index(name = "uk_tag_company_name", columnList = "company_id, name", unique = true)
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // Tag pertence a uma empresa

    // Relacionamento inverso com Contact (opcional, mas pode ser útil)
    @ManyToMany(mappedBy = "tags")
    private Set<Contact> contacts = new HashSet<>();

    public Tag(String name, Company company) {
        this.name = name;
        this.company = company;
    }
}
