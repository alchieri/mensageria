package com.br.alchieri.consulting.mensageria.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.dto.response.UserSupportMetricDTO;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface UserMetricsService {

    List<UserSupportMetricDTO> getUserMetrics(Company company, LocalDateTime start, LocalDateTime end);
}
