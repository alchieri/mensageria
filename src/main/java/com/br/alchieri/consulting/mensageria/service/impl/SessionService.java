package com.br.alchieri.consulting.mensageria.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;
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
                    .botActive(true)
                    .build();
            saveSession(session);
        }
        return session;
    }

    public void saveSession(UserSession session) {
        String key = buildKey(session.getCompanyId(), session.getPhoneNumber());
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(sessionTtlMinutes));
    }

    /**
     * Um atendente "puxa" o atendimento para si.
     */
    public void assignAgent(UserSession session, User agent) {
        
        // Validação: Se já tiver outro atendente, não pode puxar (ou precisa forçar)
        if (session.getAssignedUserId() != null && !session.getAssignedUserId().equals(agent.getId())) {
            throw new BusinessException("Esta conversa já está em atendimento por " + session.getAssignedUserName());
        }

        session.setBotActive(false); // Mata o bot
        session.setCurrentState(ConversationState.IN_SERVICE_HUMAN.name());
        
        session.setAssignedUserId(agent.getId());
        session.setAssignedUserName(agent.getUsername());
        session.setAssignedUserEmail(agent.getEmail());
        session.setAssignmentTime(LocalDateTime.now().toString());
        
        saveSession(session);
        log.info("Atendimento iniciado: Agente {} assumiu conversa com {}", agent.getEmail(), session.getPhoneNumber());
    }

    /**
     * Finaliza o atendimento humano e devolve para o Bot (ou estado IDLE).
     */
    public void finishAgentService(UserSession session) {
        
        session.setAssignedUserId(null);
        session.setAssignedUserName(null);
        session.setAssignedUserEmail(null);
        session.setAssignmentTime(null);
        
        session.setBotActive(true);
        session.setCurrentBotId(null);
        session.setCurrentStepId(null);
        
        resetSession(session);
    }

    public void updateState(UserSession session, ConversationState newState) {
        
        session.setCurrentState(newState.name());
        saveSession(session);
        log.debug("Estado atualizado para {} (User: {})", newState, session.getPhoneNumber());
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
        
        return KEY_PREFIX + companyId + ":" + phoneNumber;
    }
}
