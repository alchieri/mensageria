package com.br.alchieri.consulting.mensageria.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "whatsapp_phone_numbers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppPhoneNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @Column(name = "phone_number_id", nullable = false, unique = true)
    private String phoneNumberId; // ID da Meta (ex: 100555...)

    @Column(name = "waba_id", nullable = false)
    private String wabaId; // WABA a qual este número pertence

    @Column(name = "display_phone_number")
    private String displayPhoneNumber; // O número visível (ex: +55 11...)

    @Column(name = "alias")
    private String alias; // Nome amigável (ex: "Comercial SP", "Suporte")

    @Column(name = "is_default")
    private boolean isDefault; // Se true, é o número usado quando 'from' não é informado

    @Column(name = "quality_rating")
    private String qualityRating; // GREEN, YELLOW, RED

    @Column(name = "status")
    private String status; // CONNECTED, DISCONNECTED, BANNED

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
