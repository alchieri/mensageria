package com.br.alchieri.consulting.mensageria.catalog.dto.meta;

import java.util.List;

import lombok.Data;

@Data
public class MetaBusinessInfoDTO {
    
    private String id; // ID do Usuário (System User)
    private BusinessList businesses;

    @Data
    public static class BusinessList {
        private List<MetaBusinessDetails> data;
    }

    @Data
    public static class MetaBusinessDetails {
        private String id;   
        private String name; 
    }
}
