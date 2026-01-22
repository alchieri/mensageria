package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Dados para agendar uma nova campanha de envio de mensagens em massa.")
public class ScheduleCampaignRequest {

    @NotBlank
    private String campaignName;

    @NotBlank
    private String templateName;

    @NotBlank
    private String languageCode;

    @NotNull
    @Future(message = "A data de agendamento deve ser no futuro.")
    private LocalDateTime scheduledAt;

    @Schema(description = "Lista de IDs de tags. Todos os contatos com pelo menos uma dessas tags serão incluídos. Use isto OU a lista de contactIds.")
    private List<Long> tagIds;

    @Schema(description = "Lista de IDs de contatos individuais. Use isto OU a lista de tagIds.")
    private List<Long> contactIds;

    // @NotEmpty(message = "O mapeamento de componentes do template é obrigatório.")
    @Valid // Para validar internamente
    @Schema(description = "Mapeamento dos componentes do template (HEADER, BODY) para os dados dos contatos.")
    private List<TemplateComponentMapping> components;

    // --- DTO Interno para o Mapeamento ---
    @Data
    @Schema(name = "TemplateComponentMappingInput")
    public static class TemplateComponentMapping {
        @NotBlank
        @Schema(description = "Tipo do componente.", allowableValues = {"header", "body", "buttons"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        // Para HEADER (texto ou mídia)
        @Schema(description = "Lista de mapeamentos para as variáveis do HEADER ({{1}}, {{2}}, etc.). A ordem corresponde ao número da variável.")
        private List<ParameterMapping> headerParameters;

        // Para BODY
        @Schema(description = "Lista de mapeamentos para as variáveis do BODY ({{1}}, {{2}}, etc.). A ordem corresponde ao número da variável.")
        private List<ParameterMapping> bodyParameters;

        // Para BUTTONS (novo)
        @Schema(description = "Lista de mapeamentos para os botões dinâmicos (ex: URL com variável).")
        private List<ButtonMapping> buttonParameters;
    }

    @Data
    @Schema(name = "ButtonMappingInput")
    public static class ButtonMapping {
        @NotNull
        @Schema(description = "O índice do botão (começando em 0) que este mapeamento preencherá.", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        private Integer index; // Posição do botão na lista de botões do template

        @NotEmpty
        @Schema(description = "Lista de mapeamentos para as variáveis na URL do botão (ex: {{1}}). A ordem corresponde ao número da variável.", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<ParameterMapping> urlParameters;
    }

    @Data
    @Schema(name = "ParameterMappingInput", description = "Define a regra para preencher uma única variável de template (ex: {{1}}).")
    public static class ParameterMapping {
        @NotBlank
        @Schema(description = "Tipo do parâmetro, deve corresponder ao esperado pelo template.",
                allowableValues = {"text", "currency", "date_time", "image", "document", "video"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        @Schema(description = "Fonte do dado a partir de uma entidade. Formatos: 'contact.fieldName', 'company.fieldName', 'user.fieldName'. " +
                          "Para campos customizados do contato: 'contact.customFields.your_key'. Use isto OU 'fixedValue' OU 'payloadValue'.",
                example = "contact.name")
        private String sourceField;

        @Schema(description = "Valor fixo para o parâmetro. Use isto OU 'sourceField' OU 'payloadValue'.",
                example = "Equipe de Suporte")
        private String fixedValue;

        // Este campo não será usado no agendamento/bulk, mas sim no envio individual
        @Schema(description = "Valor dinâmico fornecido no momento da requisição. Use isto OU 'sourceField' OU 'fixedValue'.")
        private String payloadValue;

        // --- Opções Específicas do Tipo ---
        @Schema(description = "Código da moeda (ex: BRL) para parâmetros do tipo 'currency' quando a fonte é um número.")
        private String currencyCode;
        
        @Schema(description = "Formato da data para parâmetros do tipo 'date_time'. Se não fornecido, usa o formato padrão.", example = "dd/MM/yyyy")
        private String dateTimeFormat;
    }
}
