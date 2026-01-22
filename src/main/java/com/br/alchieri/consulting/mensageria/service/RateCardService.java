package com.br.alchieri.consulting.mensageria.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.dto.request.RateCardRequest;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;

public interface RateCardService {

    Page<MetaRateCard> listAllRates(Pageable pageable);
    Optional<MetaRateCard> findRateById(Long id);
    MetaRateCard createRate(RateCardRequest request);
    MetaRateCard updateRate(Long id, RateCardRequest request);
    void deleteRate(Long id);
}
