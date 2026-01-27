package com.br.alchieri.consulting.mensageria.service;

import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;

public interface PlatformConfigService {

    void uploadFlowPublicKey(String publicKeyPem, Long companyId);

    String getFlowPublicKeyId(Long companyId);

    ApiResponse uploadMetaRateCard(MultipartFile file, LocalDate effectiveDate);

    ApiResponse uploadMetaVolumeTiers(MultipartFile file, LocalDate effectiveDate);
}
