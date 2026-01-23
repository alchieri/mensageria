package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.UserSupportMetricDTO;
import com.br.alchieri.consulting.mensageria.chat.repository.InternalMessageReadReceiptRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.WhatsAppMessageLogRepository;
import com.br.alchieri.consulting.mensageria.chat.service.UserMetricsService;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserMetricsServiceImpl implements UserMetricsService {

    private final UserRepository userRepository;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final InternalMessageReadReceiptRepository readReceiptRepository;

    @Override
    public List<UserSupportMetricDTO> getUserMetrics(Company company, LocalDateTime start, LocalDateTime end) {
        // 1. Buscar todos os usuários da empresa (para garantir que quem tem 0 também apareça)
        List<User> users = userRepository.findByCompany(company, Pageable.unpaged()).getContent();

        // 2. Buscar Agregações
        Map<Long, Long> messagesSentMap = toMap(messageLogRepository.countMessagesByUser(company, start, end));
        Map<Long, Long> distinctContactsMap = toMap(messageLogRepository.countDistinctContactsByUser(company, start, end));
        Map<Long, Long> readsMap = toMap(readReceiptRepository.countReadsByUser(company, start, end));

        // 3. Montar DTOs
        List<UserSupportMetricDTO> result = new ArrayList<>();
        
        for (User user : users) {
            long sent = messagesSentMap.getOrDefault(user.getId(), 0L);
            long contacts = distinctContactsMap.getOrDefault(user.getId(), 0L);
            long reads = readsMap.getOrDefault(user.getId(), 0L);

            // Opcional: Filtrar usuários sem atividade
            // if (sent == 0 && contacts == 0 && reads == 0) continue; 

            result.add(UserSupportMetricDTO.builder()
                    .userId(user.getId())
                    .userName(user.getUsername())
                    .userEmail(user.getEmail())
                    .messagesSent(sent)
                    .distinctContactsHandled(contacts)
                    .chatsViewed(reads)
                    .build());
        }

        return result;
    }

    // Helper para converter List<MetricCountDTO> em Map<UserId, Count>
    private Map<Long, Long> toMap(List<MetricCountDTO> dtos) {
        return dtos.stream().collect(Collectors.toMap(MetricCountDTO::getUserId, MetricCountDTO::getCount));
    }
}
