package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;
import java.util.Set;

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest.TemplateComponentMapping;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para iniciar um envio em massa de mensagens de template com variáveis dinâmicas.")
public class BulkMessageTemplateRequest {

    @NotBlank(message = "O nome do template é obrigatório.")
    @Schema(description = "Nome exato do template pré-aprovado a ser enviado.", example = "oferta_especial_junho", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    @NotBlank(message = "O código do idioma é obrigatório.")
    @Schema(description = "Código do idioma do template (ex: pt_BR).", example = "pt_BR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String languageCode;

    @Valid
    @Schema(description = "Lista de REGRAS de mapeamento para preencher variáveis dinamicamente para cada contato (ex: mapear {{1}} para 'contact.name').")
    private List<ScheduleCampaignRequest.TemplateComponentMapping> components;

    @NotNull(message = "É necessário definir o público-alvo (targeting).")
    @Valid
    @Schema(description = "Define os contatos que receberão a mensagem.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Targeting targeting;

    // --- Classes Internas ---

    @Data
    @Schema(description = "Define o critério de segmentação dos contatos.")
    public static class Targeting {
        @Schema(description = "Lista de tags. A mensagem será enviada para todos os contatos que possuam QUALQUER uma das tags listadas.")
        @Size(min = 1, message = "A lista de tags não pode ser vazia se fornecida.")
        private Set<String> byTags;

        @Schema(description = "Lista explícita de números de telefone para enviar. Substitui a segmentação por tags se ambos forem fornecidos.")
        @Size(min = 1, message = "A lista de números não pode ser vazia se fornecida.")
        private List<String> byPhoneNumbers;

        // Futuramente, pode ter bySegments, byLists, etc.
    }
}
