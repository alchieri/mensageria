package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SendMultiProductMessageRequest {

    @NotBlank(message = "O destinatário (to) é obrigatório.")
    private String to;

    @NotBlank(message = "O texto do cabeçalho é obrigatório para Multi-Product.")
    private String headerText;

    @NotBlank(message = "O texto do corpo é obrigatório.")
    private String bodyText;

    private String footerText;

    // Opcional: Se não enviado, usa o da Company
    private String catalogId;

    @NotEmpty(message = "É necessário pelo menos uma seção com produtos.")
    private List<ProductSectionRequest> sections;

    @Data
    public static class ProductSectionRequest {
        @NotBlank(message = "Título da seção é obrigatório.")
        private String title;

        @NotEmpty(message = "Lista de IDs de produtos é obrigatória.")
        private List<String> productRetailerIds;
    }
}
