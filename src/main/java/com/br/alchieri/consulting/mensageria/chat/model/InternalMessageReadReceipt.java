package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.model.User;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class InternalMessageReadReceipt {
    
    @Id @GeneratedValue
    private Long id;
    
    @ManyToOne
    private User agent; // Quem leu
    
    @ManyToOne
    private Contact contact; // Conversa de quem
    
    private LocalDateTime readAt; // Quando abriu a tela
}
