package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "ID do catálogo de produtos. Se não fornecido, o catálogo padrão da empresa será usado.")
    private String catalogId;

    @NotEmpty(message = "É necessário pelo menos uma seção com produtos.")
    private List<ProductSectionRequest> sections;

    @Schema(description = "ID do telefone (Meta ID) que enviará a mensagem. Se nulo, usa o padrão da empresa.", example = "10555...")
    private String fromPhoneNumberId;

    @Data
    public static class ProductSectionRequest {
        @NotBlank(message = "Título da seção é obrigatório.")
        private String title;

        @NotEmpty(message = "Lista de IDs de produtos é obrigatória.")
        private List<String> productRetailerIds;
    }
}
