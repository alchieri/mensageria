package com.br.alchieri.consulting.mensageria.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledMessage;

@Repository
public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long> {

    // Busca um lote de mensagens pendentes que já deveriam ter sido enviadas
    @Query("SELECT sm FROM ScheduledMessage sm WHERE sm.status = 'PENDING' AND sm.scheduledAt <= :now")
    List<ScheduledMessage> findReadyToSend(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("UPDATE ScheduledMessage sm SET sm.status = :status WHERE sm.campaign.id = :campaignId AND sm.status = 'PENDING'")
    void updateStatusForPendingMessagesByCampaign(@Param("campaignId") Long campaignId, @Param("status") ScheduledMessage.MessageStatus status);

    long countByCampaignAndStatus(ScheduledCampaign campaign, ScheduledMessage.MessageStatus status);
}