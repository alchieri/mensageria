package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;
import com.br.alchieri.consulting.mensageria.model.Company;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "bots")
@Data
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    private String name;

    @Column(name = "is_active")
    private boolean isActive = true;

    // Regras de Ativação
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type")
    private BotTriggerType triggerType; 

    // Janela de Tempo (se triggerType for TIME_WINDOW)
    private LocalTime startTime;
    private LocalTime endTime;
    
    // Dias da semana (ex: "1,2,3,4,5" para Seg-Sex)
    private String activeDays; 

    // O primeiro passo do fluxo
    @OneToOne
    @JoinColumn(name = "root_step_id")
    private BotStep rootStep;

    @OneToMany(mappedBy = "bot", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BotStep> steps = new ArrayList<>();
}
