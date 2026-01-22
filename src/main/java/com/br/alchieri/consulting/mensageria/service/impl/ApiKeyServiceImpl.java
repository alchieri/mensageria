package com.br.alchieri.consulting.mensageria.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.ApiKeyRepository;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.ApiKey;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.ApiKeyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    /**
     * Gera uma nova API Key.
     * Retorna a string da chave (plain text) APENAS AQUI. O banco guarda apenas o hash.
     */
    @Override
    @Transactional
    public String createApiKey(User user, String name, Integer daysToExpire) {
        
        // 1. Gerar parte aleatória (32 bytes)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = base64Encoder.encodeToString(randomBytes);
        
        // Prefixo para facilitar identificação (padrão Stripe/moderno)
        String fullKey = "sk_live_" + token;

        // 2. Hash da chave para salvar no banco
        String hashedKey = hashKey(fullKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setName(name);
        apiKey.setKeyPrefix(fullKey.substring(0, 15)); // Guarda o prefixo visível
        apiKey.setKeyHash(hashedKey);
        apiKey.setActive(true);
        if (daysToExpire != null && daysToExpire > 0) {
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(daysToExpire));
        }

        apiKeyRepository.save(apiKey);
        
        log.info("API Key '{}' criada para usuário ID {}", name, user.getId());
        
        return fullKey; // Retorna a chave para ser mostrada UMA VEZ ao usuário
    }

    @Override
    @Transactional
    public void revokeApiKey(Long keyId, User owner) {
        
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new BusinessException("API Key não encontrada."));
        
        if (!apiKey.getUser().getId().equals(owner.getId())) {
             throw new BusinessException("Você não tem permissão para revogar esta chave.");
        }

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
        log.info("API Key ID {} revogada pelo usuário.", keyId);
    }

    @Override
    public List<ApiKey> listKeys(User user) {
        return apiKeyRepository.findByUserAndActiveTrue(user);
    }

    /**
     * Valida a chave e atualiza auditoria (lastUsedAt).
     * Retorna a entidade ApiKey se válida, ou Empty se inválida.
     */
    @Override
    @Transactional(noRollbackFor = Exception.class) // Garante que o update de lastUsedAt ocorra
    public Optional<ApiKey> validateAndAudit(String rawKey) {
        String hash = hashKey(rawKey);
        
        Optional<ApiKey> optKey = apiKeyRepository.findByKeyHash(hash);
        
        if (optKey.isPresent()) {
            ApiKey key = optKey.get();
            if (!key.isActive()) return Optional.empty();
            
            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty();
            }

            // Auditoria: Atualiza data de uso
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
            
            return Optional.of(key);
        }
        
        return Optional.empty();
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao hashear API Key", e);
        }
    }
}