package com.br.alchieri.consulting.mensageria.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "bot_options")
@Data
public class BotOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Isso gera o método setStep() que o serviço espera
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id") // Nome da coluna no banco
    @JsonIgnore // Evita loop JSON
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BotStep step;

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
