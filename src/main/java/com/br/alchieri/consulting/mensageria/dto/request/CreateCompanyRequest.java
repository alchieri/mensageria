package com.br.alchieri.consulting.mensageria.dto.request;

import com.br.alchieri.consulting.mensageria.chat.dto.request.AddressRequestDTO;
import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para um administrador BSP criar uma nova empresa cliente.")
public class CreateCompanyRequest {

    @NotBlank @Size(max = 255)
    private String name;
    @Size(max = 20)
    private String documentNumber; // CNPJ
    @Valid
    private AddressRequestDTO address;
    private String contactEmail;
    private String contactPhoneNumber;
    private String callbackUrl;
    private String templateStatusCallbackUrl;
    @Schema(defaultValue = "NOT_STARTED")
    private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;
    private Boolean enabled = true;
    @Schema(description = "Tempo de inatividade (em minutos) para expirar a sessão do bot.", example = "30")
    private Integer botSessionTtl;
    @Schema(description = "ID do Flow do Meta para coleta de endereço no checkout.")
    private String checkoutAddressFlowId;
}
