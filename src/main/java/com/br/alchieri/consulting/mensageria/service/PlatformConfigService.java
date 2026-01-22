package com.br.alchieri.consulting.mensageria.service;

import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;

import reactor.core.publisher.Mono;

public interface PlatformConfigService {

    /**
     * Faz o upload de uma chave pública para o App da Meta associado ao BSP.
     * A Meta assina a chave e retorna um ID.
     * @param publicKeyPem A chave pública em formato PEM.
     * @param companyId O ID da empresa para a qual a chave será associada.
     * @return Um Mono contendo o ID da chave pública retornado pela Meta.
     */
    Mono<String> uploadFlowPublicKey(String publicKeyPem, Long companyId);

    Mono<String> getFlowPublicKeyId(Long companyId);

    ApiResponse uploadMetaRateCard(MultipartFile file, LocalDate effectiveDate);

    ApiResponse uploadMetaVolumeTiers(MultipartFile file, LocalDate effectiveDate);
}
