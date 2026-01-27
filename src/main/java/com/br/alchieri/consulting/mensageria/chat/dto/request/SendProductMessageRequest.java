package com.br.alchieri.consulting.mensageria.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendProductMessageRequest {

    @NotBlank(message = "O destinatário (to) é obrigatório.")
    private String to;

    @NotBlank(message = "O ID do produto (SKU/Retailer ID) é obrigatório.")
    private String productRetailerId;
    
    // Opcional: Se não enviado, usa o da Company
    private String catalogId; 
    
    private String bodyText; // Opcional para Single Product
    private String footerText; // Opcional

    @Schema(description = "ID do telefone (Meta ID) que enviará a mensagem. Se nulo, usa o padrão da empresa.", example = "10555...")
    private String fromPhoneNumberId;
}
