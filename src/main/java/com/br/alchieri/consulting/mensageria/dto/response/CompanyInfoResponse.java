package com.br.alchieri.consulting.mensageria.dto.response;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações detalhadas de uma empresa cliente.")
public class CompanyInfoResponse {

    private Long id;
    private String name;
    private String documentNumber;
    private String contactEmail;
    private String contactPhoneNumber;
    private boolean enabled;
    private String metaWabaId;
    private String metaPrimaryPhoneNumberId;
    // Adicionar outros campos se necessário

    public static CompanyInfoResponse fromEntity(Company company) {
        if (company == null) return null;
        return CompanyInfoResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .documentNumber(company.getDocumentNumber())
                .contactEmail(company.getContactEmail())
                .contactPhoneNumber(company.getContactPhoneNumber())
                .enabled(company.isEnabled())
                .metaWabaId(company.getMetaWabaId())
                .metaPrimaryPhoneNumberId(company.getMetaPrimaryPhoneNumberId())
                .build();
    }
}
