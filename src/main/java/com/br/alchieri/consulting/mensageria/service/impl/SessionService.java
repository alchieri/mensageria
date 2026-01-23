package com.br.alchieri.consulting.mensageria.service.impl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.model.enums.ConversationState;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.session.ttl-minutes}")
    private long sessionTtlMinutes;

    private static final String KEY_PREFIX = "session:";

    /**
     * Recupera a sessão ou cria uma nova (Estado IDLE) se não existir.
     */
    public UserSession getSession(Long companyId, String phoneNumber) {
        String key = buildKey(companyId, phoneNumber);
        UserSession session = (UserSession) redisTemplate.opsForValue().get(key);

        if (session == null) {
            log.debug("Nova sessão iniciada para {}", phoneNumber);
            session = UserSession.builder()
                    .companyId(companyId)
                    .phoneNumber(phoneNumber)
                    .currentState(ConversationState.IDLE.name())
                    .build();
            saveSession(session);
        }
        return session;
    }

    public void updateState(UserSession session, ConversationState newState) {
        session.setCurrentState(newState.name());
        saveSession(session);
        log.debug("Estado atualizado para {} (User: {})", newState, session.getPhoneNumber());
    }

    public void saveSession(UserSession session) {
        String key = buildKey(session.getCompanyId(), session.getPhoneNumber());
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(sessionTtlMinutes));
    }

    /**
     * Reseta o estado para IDLE e limpa dados temporários, mas mantém a sessão viva.
     */
    public void resetSession(UserSession session) {
        session.setCurrentState(ConversationState.IDLE.name());
        session.getContextData().clear();
        saveSession(session);
        log.info("Sessão resetada para IDLE (User: {})", session.getPhoneNumber());
    }

    private String buildKey(Long companyId, String phoneNumber) {
        // Ex: session:1:554599998888
        return KEY_PREFIX + companyId + ":" + phoneNumber;
    }
}
