package com.br.alchieri.consulting.mensageria.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Status de saúde da integração com o WhatsApp Business API.")
public class WhatsAppHealthStatusResponse {

    @Schema(description = "ID da empresa.")
    private Long companyId;

    @Schema(description = "Indica se a empresa possui uma WABA configurada (legado/geral).")
    private boolean wabaConfigured;

    @Schema(description = "Lista de status dos canais (números) conectados.")
    private List<ChannelStatus> channels;

    @Data
    @Builder
    public static class ChannelStatus {
        private Long id;
        
        @Schema(description = "ID do telefone na Meta.")
        private String phoneNumberId;
        
        @Schema(description = "ID da WABA à qual este número pertence.")
        private String wabaId;
        
        @Schema(description = "Número de exibição formatado.")
        private String displayPhoneNumber;
        
        @Schema(description = "Nome interno do canal.")
        private String alias;
        
        @Schema(description = "Se é o número padrão de envio.")
        private boolean isDefault;
        
        @Schema(description = "Status da conexão (CONNECTED, DISCONNECTED, BANNED).")
        private String status;
        
        @Schema(description = "Qualidade do número (GREEN, YELLOW, RED, UNKNOWN).")
        private String qualityRating;
    }
}
