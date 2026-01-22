package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dados para atualizar a configuração da Meta API de um cliente (operação de Admin).")
public class UpdateClientMetaConfigRequest {

    @NotBlank // Ou pode ser opcional se quiser permitir limpar
    @Schema(description = "Novo ID do Número de Telefone da Meta Cloud API do cliente.", example = "111222333444")
    private String metaPhoneNumberId;

    @NotBlank // Ou opcional
    @Schema(description = "Novo ID da Conta do WhatsApp Business (WABA) da Meta do cliente.", example = "555666777888")
    private String metaWabaId;

    @Schema(description = "ID do Facebook Business Manager do cliente.", example = "999000111222")
    private String facebookBusinessManagerId;

    // Não inclua o Access Token da Meta aqui para ser atualizado por este DTO simples.
    // A gestão de tokens da Meta é mais sensível e deve ter um fluxo próprio.
}
