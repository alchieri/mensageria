package com.br.alchieri.consulting.mensageria.chat.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateComponentRequest;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledMessage;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledCampaignRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledMessageRepository;
import com.br.alchieri.consulting.mensageria.chat.service.CallbackService;
import com.br.alchieri.consulting.mensageria.chat.util.TemplateParameterGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageScheduler {

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ScheduledCampaignRepository campaignRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final TemplateParameterGenerator parameterGenerator;
    private final CallbackService callbackService;

    @Value("${sqs.queue.outgoing}")
    private String outgoingQueueName;

    private static final int BATCH_SIZE = 100; // Tamanho do lote a ser processado por vez

    // Roda a cada minuto. Ajuste o cron/fixedRate conforme necessário.
    @Scheduled(fixedRate = 60000) // 60000 ms = 1 minuto
    @Transactional
    public void processPendingMessages() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        // Busca um lote de mensagens prontas para envio
        List<ScheduledMessage> messagesToSend = scheduledMessageRepository
                .findReadyToSend(LocalDateTime.now(), pageable);

        if (messagesToSend.isEmpty()) {
            log.trace("Nenhuma mensagem agendada para enviar no momento.");
            return;
        }

        // Agrupa mensagens por campanha para processamento
        Map<ScheduledCampaign, List<ScheduledMessage>> messagesByCampaign = messagesToSend.stream()
                .collect(Collectors.groupingBy(ScheduledMessage::getCampaign));

        log.info("Encontradas {} mensagens para enfileirar, distribuídas em {} campanhas.", messagesToSend.size(), messagesByCampaign.size());

        for (Map.Entry<ScheduledCampaign, List<ScheduledMessage>> entry : messagesByCampaign.entrySet()) {
            ScheduledCampaign campaign = entry.getKey();
            List<ScheduledMessage> campaignMessages = entry.getValue();

            // Pula campanhas que não estão em estado de processamento
            if (campaign.getStatus() != ScheduledCampaign.CampaignStatus.PENDING &&
                campaign.getStatus() != ScheduledCampaign.CampaignStatus.PROCESSING) {
                log.warn("Pulando lote para campanha ID {} pois seu status é {}.", campaign.getId(), campaign.getStatus());
                continue;
            }

            // Move a campanha para PROCESSING se ainda estiver PENDING
            if (campaign.getStatus() == ScheduledCampaign.CampaignStatus.PENDING) {
                campaign.setStatus(ScheduledCampaign.CampaignStatus.PROCESSING);
                campaignRepository.save(campaign);
            }

            // Enfileira as mensagens do lote
            for (ScheduledMessage msg : campaignMessages) {
                try {
                    // 1. Gera os componentes com parâmetros resolvidos para o contato da mensagem agendada
                    List<TemplateComponentRequest> resolvedComponents = parameterGenerator.generateComponents(
                            msg.getCampaign().getComponentMappingsJson(),
                            msg.getContact(),
                            msg.getCampaign().getCompany(),
                            msg.getCampaign().getCreatedByUser() // Passa o usuário que criou a campanha
                    );

                    // 2. Monta o SendTemplateMessageRequest para este envio específico
                    SendTemplateMessageRequest templateRequestPayload = SendTemplateMessageRequest.builder()
                            .to(msg.getContact().getPhoneNumber())
                            .contactId(msg.getContact().getId())
                            .templateName(msg.getCampaign().getTemplateName())
                            .languageCode(msg.getCampaign().getLanguageCode())
                            .resolvedComponents(resolvedComponents) // Passa os componentes JÁ RESOLVIDOS
                            .build();

                    // 3. Monta o payload para a fila SQS
                    OutgoingMessageRequest queuePayload = OutgoingMessageRequest.builder()
                            .messageType("TEMPLATE")
                            .userId(msg.getCampaign().getCreatedByUser().getId())
                            .templateRequest(templateRequestPayload)
                            .scheduledMessageId(msg.getId()) // Mantém o vínculo com a mensagem agendada
                            .originalRequestId(MDC.get("traceId") != null ? MDC.get("traceId") : "scheduler-" + UUID.randomUUID())
                            .build();

                    String jsonPayload = objectMapper.writeValueAsString(queuePayload);
                    String messageGroupId = "campaign-" + msg.getCampaign().getId();

                    // 4. Envia para o SQS
                    sqsTemplate.send(to -> to.queue(outgoingQueueName).payload(jsonPayload).header("message-group-id", messageGroupId));

                    // 5. Atualiza o status
                    msg.setStatus(ScheduledMessage.MessageStatus.QUEUED);
                } catch (Exception e) {
                    log.error("Falha ao enfileirar mensagem agendada ID {}: {}", msg.getId(), e.getMessage());
                    msg.setStatus(ScheduledMessage.MessageStatus.FAILED);
                    msg.setFailureReason("Falha ao enfileirar: " + e.getMessage());
                }
            } // Fim do loop de mensagens do lote

            // ***** LÓGICA PARA MARCAR COMO COMPLETED *****
            // Após processar o lote, verifica se ainda há mensagens PENDENTES para esta campanha
            long remainingPending = scheduledMessageRepository.countByCampaignAndStatus(campaign, ScheduledMessage.MessageStatus.PENDING);
            
            if (remainingPending == 0) {
                // Se não há mais mensagens pendentes, a campanha foi completamente enfileirada.
                log.info("Todas as mensagens da campanha ID {} (Nome: '{}') foram enfileiradas. Marcando como COMPLETED.",
                         campaign.getId(), campaign.getCampaignName());
                campaign.setStatus(ScheduledCampaign.CampaignStatus.COMPLETED);
                ScheduledCampaign completedCampaign = campaignRepository.save(campaign);
                try {
                    callbackService.sendCampaignStatusCallback(completedCampaign.getCompany().getId(), completedCampaign.getId());
                } catch (Exception e) {
                    // Logar erro na chamada do callback, mas não deixar que isso afete o scheduler
                    log.error("Falha ao iniciar o envio de callback de conclusão para campanha ID {}: {}",
                            completedCampaign.getId(), e.getMessage());
                }
            } else {
                log.info("{} mensagens pendentes ainda restam para a campanha ID {}.", remainingPending, campaign.getId());
            }
        } // Fim do loop de campanhas
        log.info("Lote de {} mensagens agendadas processado.", messagesToSend.size());
    }
}
