package com.br.alchieri.consulting.mensageria.chat.model;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.model.User;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "internal_message_read_receipts")
@Data
@NoArgsConstructor
public class InternalMessageReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // O atendente que visualizou

    @ManyToOne
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact; // A conversa visualizada

    private LocalDateTime readAt;

    @PrePersist
    public void prePersist() {
        this.readAt = LocalDateTime.now();
    }
}
