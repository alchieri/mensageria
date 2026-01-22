package com.br.alchieri.consulting.mensageria.service;

import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.model.Company;

import reactor.core.publisher.Mono;

public interface HealthCheckService {

    Mono<WhatsAppHealthStatusResponse> checkWhatsAppConfigStatus(Company company);
}
