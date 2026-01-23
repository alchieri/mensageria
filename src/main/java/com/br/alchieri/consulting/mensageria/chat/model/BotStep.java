package com.br.alchieri.consulting.mensageria.chat.model;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "bot_steps")
@Data
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

    // As opções que saem deste passo (ex: Botões 1, 2, 3)
    @OneToMany(mappedBy = "originStep", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BotOption> options;
}
