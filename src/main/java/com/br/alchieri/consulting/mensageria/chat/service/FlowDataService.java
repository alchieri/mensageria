package com.br.alchieri.consulting.mensageria.chat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface FlowDataService {

    String processEncryptedFlowData(String encryptedBody, String signature);

    Page<FlowData> getFlowDataByFlowId(Long flowId, Company company, Pageable pageable);
}
