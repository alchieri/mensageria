package com.br.alchieri.consulting.mensageria.dto.response;

import java.util.List;

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
    private List<PhoneNumberSummary> phoneNumbers;
    private List<BusinessManagerSummary> businessManagers;
    private Integer botSessionTtl;
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
                .businessManagers(company.getBusinessManagers().stream().map(bm -> {
                    BusinessManagerSummary summary = new BusinessManagerSummary();
                    summary.setMetaBusinessId(bm.getMetaBusinessId());
                    summary.setName(bm.getName());
                    return summary;
                }).toList())
                .phoneNumbers(company.getPhoneNumbers().stream().map(phoneNumber -> {
                    PhoneNumberSummary summary = new PhoneNumberSummary();
                    summary.setId(phoneNumber.getId());
                    summary.setDisplayPhoneNumber(phoneNumber.getDisplayPhoneNumber());
                    summary.setAlias(phoneNumber.getAlias());
                    summary.setDefault(phoneNumber.isDefault());
                    summary.setStatus(phoneNumber.getStatus());
                    return summary;
                }).toList())
                .botSessionTtl(company.getBotSessionTtl())
                .build();
    }

    @Data
    public static class PhoneNumberSummary {
        private Long id;
        private String displayPhoneNumber;
        private String alias;
        private boolean isDefault;
        private String status;
    }

    @Data
    public static class BusinessManagerSummary {
        private String metaBusinessId;
        private String name;
    }
}
