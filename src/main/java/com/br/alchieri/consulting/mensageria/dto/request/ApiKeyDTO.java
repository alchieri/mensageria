package com.br.alchieri.consulting.mensageria.dto.request;

import lombok.Data;

@Data
public class ApiKeyDTO {

    private Long id;
    private String name;
    private String maskedKey;
    private String lastUsed;
    private String createdAt;
    
    public ApiKeyDTO(Long id, String name, String maskedKey, String lastUsed, String createdAt) {
        this.id = id;
        this.name = name;
        this.maskedKey = maskedKey;
        this.lastUsed = lastUsed;
        this.createdAt = createdAt;
    }
}
