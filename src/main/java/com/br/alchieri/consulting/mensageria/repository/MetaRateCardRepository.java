package com.br.alchieri.consulting.mensageria.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;

public interface MetaRateCardRepository extends JpaRepository<MetaRateCard, Long> {

    /**
     * Encontra a tarifa efetiva da Meta para uma combinação de país, categoria e volume de mensagens,
     * considerando a data efetiva mais recente.
     *
     * @param countryCode O código do país (ex: "55").
     * @param category A categoria do template (MARKETING, UTILITY, AUTHENTICATION).
     * @param currentMonthVolume O volume de mensagens já enviado no mês.
     * @param date A data da consulta.
     * @return Um Optional contendo a tarifa correspondente.
     */
    @Query("SELECT r FROM MetaRateCard r " +
           "WHERE r.marketName = :marketName " +
           "AND r.category = :category " +
           "AND r.volumeTierStart <= :currentMonthVolume " +
           "AND (r.volumeTierEnd IS NULL OR r.volumeTierEnd >= :currentMonthVolume) " +
           "AND r.effectiveDate <= :date " +
           "ORDER BY r.effectiveDate DESC " +
           "LIMIT 1")
    Optional<MetaRateCard> findEffectiveRate(@Param("marketName") String marketName,
                                             @Param("category") TemplateCategory category,
                                             @Param("currentMonthVolume") Long currentMonthVolume, // Usar Long para volume
                                             @Param("date") LocalDate date);

    /**
    * Busca uma entrada de tarifa existente pela combinação única.
    */
    Optional<MetaRateCard> findByMarketNameAndCategoryAndEffectiveDateAndVolumeTierStart(
            String marketName,
            TemplateCategory category,
            LocalDate effectiveDate,
            Long volumeTierStart
    );
}
