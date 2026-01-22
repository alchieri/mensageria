package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest.TemplateComponentMapping;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor // Necessário para o Jackson e JPA
@AllArgsConstructor // Necessário para o Builder funcionar bem
@Schema(description = "Dados para enviar uma mensagem de template. Forneça 'contactId' para usar variáveis dinâmicas de um contato salvo, OU 'to' (número de telefone) com parâmetros resolvidos, OU 'to' com parâmetros dinâmicos do payload.")
public class SendTemplateMessageRequest {

    @Schema(description = "ID do contato (do seu banco de dados) para quem a mensagem será enviada. Se fornecido, o campo 'to' será ignorado.")
    private Long contactId;
    
    @Size(min = 10, max = 15)
    @Schema(description = "Número de telefone do destinatário no formato E.164. Necessário se 'contactId' não for fornecido.")
    private String to;

    @NotBlank(message = "O nome do template é obrigatório.")
    @Schema(description = "Nome exato do template pré-aprovado.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    @NotBlank(message = "O código do idioma é obrigatório.")
    @Schema(description = "Código do idioma do template.", example = "pt_BR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String languageCode;

    @Valid
    @Schema(description = "REGRAS de mapeamento para preencher variáveis dinamicamente. Use em conjunto com 'contactId' para envio imediato dinâmico.")
    private List<TemplateComponentMapping> components;

    @Valid
    @Schema(description = "Componentes com parâmetros JÁ RESOLVIDOS. Usado para envio direto (com 'to') ou por jobs em massa/agendados.")
    private List<TemplateComponentRequest> resolvedComponents;

    
}
