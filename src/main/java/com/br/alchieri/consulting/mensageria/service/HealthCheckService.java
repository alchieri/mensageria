package com.br.alchieri.consulting.mensageria.service;

import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface HealthCheckService {

    WhatsAppHealthStatusResponse checkWhatsAppConfigStatus(Company company);
}
