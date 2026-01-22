package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.MetaRateCardRepository;
import com.br.alchieri.consulting.mensageria.dto.request.RateCardRequest;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;
import com.br.alchieri.consulting.mensageria.service.RateCardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateCardServiceImpl implements RateCardService {

    private final MetaRateCardRepository rateCardRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<MetaRateCard> listAllRates(Pageable pageable) {
        return rateCardRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MetaRateCard> findRateById(Long id) {
        return rateCardRepository.findById(id);
    }

    @Override
    @Transactional
    public MetaRateCard createRate(RateCardRequest request) {
        // Verifica se uma tarifa com a mesma chave única já existe
        rateCardRepository.findByMarketNameAndCategoryAndEffectiveDateAndVolumeTierStart(
                request.getMarketName(), request.getCategory(), request.getEffectiveDate(), request.getVolumeTierStart()
        ).ifPresent(existing -> {
            throw new BusinessException("Uma tarifa com esta combinação de mercado, categoria, data e faixa de volume já existe.");
        });

        MetaRateCard newRate = new MetaRateCard();
        mapDtoToEntity(request, newRate);
        log.info("Criando nova tarifa para o mercado '{}', categoria '{}'", request.getMarketName(), request.getCategory());
        return rateCardRepository.save(newRate);
    }

    @Override
    @Transactional
    public MetaRateCard updateRate(Long id, RateCardRequest request) {
        MetaRateCard rateToUpdate = rateCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa com ID " + id + " não encontrada."));

        // Verifica se a atualização criaria uma duplicata
        rateCardRepository.findByMarketNameAndCategoryAndEffectiveDateAndVolumeTierStart(
                request.getMarketName(), request.getCategory(), request.getEffectiveDate(), request.getVolumeTierStart()
        ).ifPresent(existing -> {
            if (!existing.getId().equals(id)) { // Garante que não é a mesma entidade
                throw new BusinessException("A atualização criaria uma tarifa duplicada que já existe.");
            }
        });

        mapDtoToEntity(request, rateToUpdate);
        log.info("Atualizando tarifa ID {} (Mercado: {})", id, request.getMarketName());
        return rateCardRepository.save(rateToUpdate);
    }

    @Override
    @Transactional
    public void deleteRate(Long id) {
        if (!rateCardRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tarifa com ID " + id + " não encontrada para exclusão.");
        }
        log.info("Excluindo tarifa ID {}", id);
        rateCardRepository.deleteById(id);
    }
    
    // Método helper para mapear DTO para Entidade
    private void mapDtoToEntity(RateCardRequest dto, MetaRateCard entity) {
        entity.setMarketName(dto.getMarketName());
        entity.setCountryCode(dto.getCountryCode());
        entity.setCurrency(dto.getCurrency());
        entity.setCategory(dto.getCategory());
        entity.setVolumeTierStart(dto.getVolumeTierStart());
        entity.setVolumeTierEnd(dto.getVolumeTierEnd());
        entity.setRate(dto.getRate());
        entity.setEffectiveDate(dto.getEffectiveDate());
    }
}
