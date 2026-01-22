package com.br.alchieri.consulting.mensageria.dto.response;

import lombok.Data;

@Data
public class ApiKeyCreationResponse {

    private String secretKey;
    private String message;
    
    public ApiKeyCreationResponse(String secretKey, String message) {
        this.secretKey = secretKey;
        this.message = message;
    }
}
