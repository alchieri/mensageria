package com.br.alchieri.consulting.mensageria.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendMediaMessageRequest {

    @NotBlank(message = "O destinatário (to) é obrigatório.")
    private String to;

    @NotBlank(message = "O tipo de mídia é obrigatório (image, document, audio, video, sticker).")
    @Pattern(regexp = "^(image|document|audio|video|sticker)$", message = "Tipo de mídia inválido.")
    private String type;

    @NotBlank(message = "O ID da mídia (ou URL) é obrigatório.")
    private String mediaId; // O ID retornado pelo endpoint de upload da Meta

    private String caption; // Legenda (Opcional, válida para image, document, video)
    
    private String filename; // Opcional, usado apenas para documentos

    @Schema(description = "ID do telefone (Meta ID) que enviará a mensagem. Se nulo, usa o padrão da empresa.", example = "10555...")
    private String fromPhoneNumberId;
}
