package com.br.alchieri.consulting.mensageria.service;

import com.br.alchieri.consulting.mensageria.dto.request.PublicRegistrationRequest;
import com.br.alchieri.consulting.mensageria.dto.response.RegistrationResponse;

public interface RegistrationService {

    RegistrationResponse registerNewCompanyAndUser(PublicRegistrationRequest request);
}
