package com.br.alchieri.consulting.mensageria.service;

import java.util.List;
import java.util.Optional;

import com.br.alchieri.consulting.mensageria.model.ApiKey;
import com.br.alchieri.consulting.mensageria.model.User;

public interface ApiKeyService {

    String createApiKey(User user, String name, Integer daysToExpire);

    void revokeApiKey(Long keyId, User owner);

    List<ApiKey> listKeys(User user);
    
    Optional<ApiKey> validateAndAudit(String rawKey);
}
