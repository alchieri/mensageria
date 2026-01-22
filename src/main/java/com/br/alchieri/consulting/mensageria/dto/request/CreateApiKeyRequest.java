package com.br.alchieri.consulting.mensageria.dto.request;

import lombok.Data;

@Data
public class CreateApiKeyRequest {

    private String name;
    private Integer daysToExpire; // Null = nunca
}
