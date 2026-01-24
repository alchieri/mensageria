package com.br.alchieri.consulting.mensageria.chat.model;

import java.util.ArrayList;
import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "bot_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // Apenas para organização interna

    @Enumerated(EnumType.STRING)
    private BotStepType stepType; // TEXT, FLOW, TEMPLATE, HANDOFF (Transbordo)

    @Column(columnDefinition = "TEXT")
    private String content; // O texto da mensagem, ou ID do Template, ou ID do Flow

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON para config extra (ex: header de template, token de flow)

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<BotOption> options = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    @JsonIgnore // Evita loop infinito no JSON
    @ToString.Exclude // Evita StackOverflow no Lombok
    @EqualsAndHashCode.Exclude
    private Bot bot;
}
