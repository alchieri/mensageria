package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignora campos extras que a Meta possa enviar
public class MetaErrorPayload {

    @JsonProperty("message")
    private String message;

    @JsonProperty("type")
    private String type;

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("error_subcode")
    private Integer errorSubcode;

    @JsonProperty("error_user_title") // Às vezes presente
    private String errorUserTitle;

    @JsonProperty("error_user_msg") // Às vezes presente
    private String errorUserMsg;

    @JsonProperty("fbtrace_id")
    private String fbtraceId;

    // Captura dados extras que podem vir no nó 'error_data'
    @JsonProperty("error_data")
    private Map<String, Object> errorData;

    // Campo 'details' frequentemente está dentro de 'error_data'
    public String getErrorDetails() {
        if (errorData != null && errorData.containsKey("details")) {
            Object details = errorData.get("details");
            return details instanceof String ? (String) details : String.valueOf(details);
        }
        return null;
    }
}
