package com.br.alchieri.consulting.mensageria.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.br.alchieri.consulting.mensageria.chat.dto.request.AddressRequestDTO;
import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações detalhadas de uma empresa cliente, incluindo usuários e plano de cobrança.")
public class CompanyDetailsResponse {

    private Long id;
    private String name;
    private String documentNumber;
    private String contactEmail;
    private String contactPhoneNumber;
    private AddressRequestDTO address;
    private boolean enabled;

    // Meta Config
    private String metaWabaId;
    private String metaPrimaryPhoneNumberId;
    private String facebookBusinessManagerId;
    private String metaFlowPublicKeyId;

    private String metaCatalogId;

    // Callbacks
    private String generalCallbackUrl;
    private String templateStatusCallbackUrl;

    // Status
    private OnboardingStatus onboardingStatus;

    // Associated Data
    @Schema(description = "Plano de cobrança associado a esta empresa.")
    private BillingPlanResponse billingPlan;

    @Schema(description = "Lista resumida de usuários associados a esta empresa.")
    private List<UserSummaryResponse> users;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyDetailsResponse fromEntity(Company company) {
        if (company == null) return null;
        return CompanyDetailsResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .documentNumber(company.getDocumentNumber())
                .contactEmail(company.getContactEmail())
                .contactPhoneNumber(company.getContactPhoneNumber())
                .address(AddressRequestDTO.fromEntity(company.getAddress()))
                .enabled(company.isEnabled())
                .metaWabaId(company.getMetaWabaId())
                .metaPrimaryPhoneNumberId(company.getMetaPrimaryPhoneNumberId())
                .metaFlowPublicKeyId(company.getMetaFlowPublicKeyId())
                .facebookBusinessManagerId(company.getFacebookBusinessManagerId())
                .generalCallbackUrl(company.getGeneralCallbackUrl())
                .templateStatusCallbackUrl(company.getTemplateStatusCallbackUrl())
                .onboardingStatus(company.getOnboardingStatus())
                .billingPlan(BillingPlanResponse.fromEntity(company.getBillingPlan()))
                .users(company.getUsers().stream()
                              .map(UserSummaryResponse::fromEntity)
                              .collect(Collectors.toList()))
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }

    // DTO Interno para resumo do usuário
    @Data
    @Builder
    @Schema(name = "UserSummaryOutput", description = "Informações resumidas de um usuário.")
    public static class UserSummaryResponse {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private boolean enabled;

        public static UserSummaryResponse fromEntity(User user) {
            if (user == null) return null;
            return UserSummaryResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .enabled(user.isEnabled())
                    .build();
        }
    }
}
