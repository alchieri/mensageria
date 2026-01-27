package com.br.alchieri.consulting.mensageria.dto.request;

import com.br.alchieri.consulting.mensageria.chat.dto.request.AddressRequestDTO;
import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador BSP atualizar uma empresa cliente existente.")
public class UpdateCompanyRequest {

    @Size(max = 255)
    @Schema(description = "Novo nome para a empresa cliente.", example = "Cliente Fictício S.A.")
    private String name;

    @Size(max = 20)
    @Schema(description = "Novo número de documento (CNPJ/CPF) da empresa.", example = "12.345.678/0001-99")
    private String documentNumber;

    @Valid // Para validar os campos dentro de AddressRequestDTO
    @Schema(description = "Novo endereço da empresa.")
    private AddressRequestDTO address; // Reutiliza o DTO de endereço do registro

    @Email(message = "Formato de email de contato inválido.")
    @Size(max = 150)
    @Schema(description = "Novo email de contato principal da empresa.", example = "contato@clienteficticio.com")
    private String contactEmail;

    @Size(max = 20) // Ajuste conforme necessário
    @Schema(description = "Novo número de telefone de contato principal da empresa.", example = "+5511999998888")
    private String contactPhoneNumber;

    @Schema(description = "Nova URL de callback geral para esta empresa.")
    private String callbackUrl;

    @Schema(description = "Nova URL de callback para status de template para esta empresa.")
    private String templateStatusCallbackUrl;

    @Schema(description = "Novo status do processo de onboarding da empresa para a API do WhatsApp.")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Define se a conta da empresa está ativa ou desativada.")
    private Boolean enabled;

    @Schema(description = "Novo ID do Catálogo no Gerenciador de Comércio da Meta para esta empresa.")
    private String metaCatalogId;

    @Schema(description = "Tempo de inatividade (em minutos) para expirar a sessão do bot.", example = "30")
    private Integer botSessionTtl;
}
