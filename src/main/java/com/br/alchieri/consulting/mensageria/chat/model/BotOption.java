package com.br.alchieri.consulting.mensageria.chat.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "bot_options")
@Data
public class BotOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "origin_step_id")
    private BotStep originStep;

    // O próximo passo se o usuário escolher esta opção
    @ManyToOne
    @JoinColumn(name = "target_step_id")
    private BotStep targetStep;

    // O que o usuário precisa digitar/clicar para acionar isso
    // Pode ser um número ("1"), palavra-chave ("financeiro") ou regex
    private String keyword; 
    
    // Label para botões (se formos renderizar botões)
    private String label;

    // Ordem de exibição
    private Integer sequence;
    
    // Se essa opção leva a um transbordo humano direto
    private boolean isHandoff = false;
}
