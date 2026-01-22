package com.br.alchieri.consulting.mensageria.dto.response;

import java.time.Instant;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Relatório de saúde e status da configuração do WhatsApp Business para uma empresa.")
public class WhatsAppHealthStatusResponse {

    @Schema(description = "Timestamp da verificação.")
    private Instant checkedAt;

    @Schema(description = "ID da Empresa no nosso sistema.")
    private Long companyId;

    @Schema(description = "Nome da Empresa no nosso sistema.")
    private String companyName;

    @Schema(description = "Status geral da verificação (OK, WARNING, ERROR).")
    private String overallStatus;

    @Schema(description = "Detalhes da verificação da WABA (WhatsApp Business Account).")
    private WabaStatus wabaStatus;

    @Schema(description = "Detalhes da verificação do Número de Telefone.")
    private PhoneNumberStatus phoneNumberStatus;

    @Data
    @Builder
    @Schema(name = "WabaHealthStatus")
    public static class WabaStatus {
        private String id;
        private String name;
        private String timezoneId;
        private String currency;
        private String messageTemplateNamespace;
        private String checkStatus; // "FOUND", "NOT_FOUND", "ERROR"
        private String errorMessage;
    }

    @Data
    @Builder
    @Schema(name = "PhoneNumberHealthStatus")
    public static class PhoneNumberStatus {
        private String id;
        private String verifiedName;
        private String qualityRating; // "GREEN", "YELLOW", "RED"
        private String messagingLimitTier; // "TIER_1", "TIER_2", ...
        private String codeVerificationStatus; // "VERIFIED", "NOT_VERIFIED"
        private String status; // "CONNECTED", "DISCONNECTED", ...
        private String nameStatus; // "APPROVED", "PENDING", "REJECTED"
        private String checkStatus; // "FOUND", "NOT_FOUND", "ERROR"
        private String errorMessage;
    }

    public static WhatsAppHealthStatusResponse from(Company company, JsonNode wabaNode, JsonNode phoneNode) {
        WabaStatus.WabaStatusBuilder wabaBuilder = WabaStatus.builder();
        if (wabaNode != null && wabaNode.has("id")) {
            wabaBuilder.id(wabaNode.path("id").asText())
                       .name(wabaNode.path("name").asText())
                       .timezoneId(wabaNode.path("timezone_id").asText())
                       .currency(wabaNode.path("currency").asText())
                       .messageTemplateNamespace(wabaNode.path("message_template_namespace").asText())
                       .checkStatus("FOUND");
        } else if (wabaNode != null && wabaNode.has("error")) {
            wabaBuilder.id(company.getMetaWabaId())
                       .checkStatus("ERROR")
                       .errorMessage(wabaNode.path("error").path("message").asText());
        } else {
            wabaBuilder.id(company.getMetaWabaId()).checkStatus("NOT_FOUND");
        }

        PhoneNumberStatus.PhoneNumberStatusBuilder phoneBuilder = PhoneNumberStatus.builder();
        if (phoneNode != null && phoneNode.has("id")) {
            phoneBuilder.id(phoneNode.path("id").asText())
                        .verifiedName(phoneNode.path("verified_name").asText())
                        .qualityRating(phoneNode.path("quality_rating").asText())
                        .messagingLimitTier(phoneNode.path("messaging_limit_tier").asText())
                        .codeVerificationStatus(phoneNode.path("code_verification_status").asText())
                        .status(phoneNode.path("status").asText())
                        .nameStatus(phoneNode.path("name_status").asText())
                        .checkStatus("FOUND");
        } else if (phoneNode != null && phoneNode.has("error")) {
            phoneBuilder.id(company.getMetaPrimaryPhoneNumberId())
                        .checkStatus("ERROR")
                        .errorMessage(phoneNode.path("error").path("message").asText());
        } else {
            phoneBuilder.id(company.getMetaPrimaryPhoneNumberId()).checkStatus("NOT_FOUND");
        }

        WabaStatus finalWabaStatus = wabaBuilder.build();
        PhoneNumberStatus finalPhoneStatus = phoneBuilder.build();
        String overall = "OK";
        if ("ERROR".equals(finalWabaStatus.getCheckStatus()) || "ERROR".equals(finalPhoneStatus.getCheckStatus())) {
            overall = "ERROR";
        } else if ("NOT_FOUND".equals(finalWabaStatus.getCheckStatus()) || !"CONNECTED".equals(finalPhoneStatus.getStatus())) {
            overall = "WARNING";
        } else if (!"GREEN".equals(finalPhoneStatus.getQualityRating())) {
            overall = "WARNING";
        }


        return WhatsAppHealthStatusResponse.builder()
                .checkedAt(Instant.now())
                .companyId(company.getId())
                .companyName(company.getName())
                .overallStatus(overall)
                .wabaStatus(finalWabaStatus)
                .phoneNumberStatus(finalPhoneStatus)
                .build();
    }
}
