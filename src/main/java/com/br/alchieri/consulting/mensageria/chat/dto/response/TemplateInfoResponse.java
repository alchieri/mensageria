package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TemplateInfoResponse {

    private String id;
    private String name;
    private String status; // PENDING, APPROVED, REJECTED, PAUSED, DISABLED
    private String category;
    private String language;
    private List<TemplateComponentResponse> components; // Estrutura similar à de criação/envio

    // --- DTOs internos ---
    @Data
    public static class TemplateComponentResponse {
        private String type;
        private String format;
        private String text;
        private List<ButtonResponse> buttons;
        // Adicionar example se a API retornar
    }

    @Data
    public static class ButtonResponse {
        private String type;
        private String text;
        private String url;
        @JsonProperty("phone_number")
        private String phoneNumber;
        @JsonProperty("coupon_code")
        private String couponCode;
    }
}