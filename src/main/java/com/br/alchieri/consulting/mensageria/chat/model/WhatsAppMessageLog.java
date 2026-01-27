package com.br.alchieri.consulting.mensageria.chat.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UpdateTimestamp;

import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "whatsapp_message_logs", indexes = {
        @Index(name = "idx_wml_wamid", columnList = "wamid", unique = true), // Index para buscar por WAMID
        @Index(name = "idx_wml_company_timestamp", columnList = "company_id, createdAt"), // Index para buscar msg de cliente por tempo
        @Index(name = "idx_wml_scheduled_message_id", columnList = "scheduledMessageId")
})
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "companyId", type = Long.class))
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@Data
@NoArgsConstructor
public class WhatsAppMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true) // WAMID é único
    private String wamid; // WhatsApp Message ID (retornado pela Meta)

    @ManyToOne(fetch = FetchType.LAZY) // Muitos logs para um cliente
    @JoinColumn(name = "company_id") // Chave estrangeira
    private Company company; // Company associado (se aplicável e identificável)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageDirection direction; // INCOMING ou OUTGOING

    @Column(name = "sender_phone_number")
    private String senderPhoneNumber; // O número real de quem enviou (Ex: +5545999...)
                                      // Se OUTGOING: Número da Empresa
                                      // Se INCOMING: Número do Cliente

    @Column(name = "channel_id")
    private String channelId; // O ID da Meta do telefone da Empresa envolvido (Ex: 100555...)
                              // Fundamental para saber por qual "linha" a conversa ocorreu.

    @Column(nullable = false)
    private String recipient; // Número destinatário (nosso nº ou usuário final)

    @Column(nullable = false)
    private String messageType; // text, image, template, status, etc.

    @Column(columnDefinition = "TEXT") // Mensagens de texto podem ser longas
    private String content; // Corpo do texto, ou ID da mídia, ou nome do template

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON ou texto para info extra (ex: botões clicados, erros)

    @Column(nullable = false)
    private String status; // Ex: PENDING, SENT, DELIVERED, READ, FAILED, RECEIVED

    @Column(nullable = true) // Nulo para mensagens que não foram agendadas
    private Long scheduledMessageId;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp // Gerenciado pelo Hibernate
    private LocalDateTime createdAt;

    @UpdateTimestamp // Gerenciado pelo Hibernate
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime lastCallbackAttempt;

    @Column
    private String lastCallbackStatus;

    @Column(nullable = true, precision = 19, scale = 8)
    private BigDecimal metaCost; // Custo real cobrado pela Meta para esta mensagem

    @Column(nullable = true, precision = 19, scale = 8)
    private BigDecimal platformFee; // Sua taxa de plataforma para esta mensagem

    @Column(nullable = true, precision = 19, scale = 8)
    private BigDecimal finalPrice; // Preço final para o cliente (metaCost + platformFee)

    @Column(nullable = true)
    private String pricingCategory; // Categoria de preço retornada pelo webhook da Meta
}
