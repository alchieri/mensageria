package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "media_uploads", indexes = {
        @Index(name = "idx_media_company", columnList = "company_id"),
        @Index(name = "idx_media_meta_id", columnList = "metaMediaId", unique = true) // O ID da Meta é único
})
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "companyId", type = Long.class))
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@Data
@NoArgsConstructor
public class MediaUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // Empresa que fez o upload

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User uploadedBy; // Usuário que fez o upload

    @NotBlank
    @Column(unique = true, nullable = false)
    private String metaMediaId; // O ID retornado pela API da Meta

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType; // Ex: "image/jpeg", "application/pdf"

    @Column(nullable = false)
    private Long fileSize; // Tamanho do arquivo em bytes

    // Opcional: A URL da mídia pode ser obtida da Meta, mas geralmente expira.
    // É melhor usar o mediaId para obter a URL quando necessário.
    // private String metaMediaUrl;

    @NotBlank
    @Column(nullable = false)
    private String s3BucketName; // Nome do bucket onde o arquivo está

    @NotBlank
    @Column(nullable = false, unique = true) // A chave do objeto deve ser única
    private String s3ObjectKey; // O "caminho" completo do arquivo no bucket

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
