package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.request.BulkMessageTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateComponentRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BulkMessageResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.service.BulkMessageService;
import com.br.alchieri.consulting.mensageria.chat.util.TemplateParameterGenerator;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkMessageServiceImpl implements BulkMessageService {

    private final ContactRepository contactRepository;
    private final BillingService billingService;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper; // Para serialização manual no controller
    private final TemplateParameterGenerator parameterGenerator;

    @Value("${sqs.queue.outgoing}")
    private String outgoingQueueName;

    @Override
    @Transactional(readOnly = true) // Apenas lê do banco, a escrita é na fila
    public BulkMessageResponse startBulkTemplateJob(BulkMessageTemplateRequest request, Company company, User creator) {
        
        String jobId = UUID.randomUUID().toString();
        log.info("Iniciando job de envio em massa ID {} para empresa ID {}", jobId, company.getId());

        // 1. Encontrar os contatos alvo
        List<Contact> targetContacts = findTargetContacts(request.getTargeting(), company);
        if (targetContacts.isEmpty()) {
            log.warn("Job {} cancelado: Nenhum contato encontrado para os critérios de segmentação.", jobId);
            return BulkMessageResponse.builder()
                    .jobId(jobId).status("CANCELED").estimatedContactCount(0)
                    .message("Nenhum contato encontrado para os critérios fornecidos.").build();
        }

        int contactCount = targetContacts.size();
        log.info("Job {}: {} contatos encontrados para envio.", jobId, contactCount);

        // 2. Verificar se a empresa pode enviar essa quantidade de mensagens
        if (!billingService.canCompanySendMessages(company, contactCount)) {
            log.warn("Job {} bloqueado para empresa ID {}: Limite de envio de mensagens seria excedido.", jobId, company.getId());
            return BulkMessageResponse.builder()
                    .jobId(jobId).status("LIMIT_EXCEEDED").estimatedContactCount(contactCount)
                    .message("Limite de envio de mensagens seria excedido. Contate o suporte.").build();
        }

        // 3. Enfileirar uma mensagem para cada contato
        int enqueuedCount = 0;
        for (Contact contact : targetContacts) {
            try {
                // 1. Gera os componentes com parâmetros resolvidos para este contato
                List<TemplateComponentRequest> resolvedComponents = parameterGenerator.generateComponents(
                        request.getComponents(), // Passa as REGRAS de mapeamento
                        contact,                 // Passa o CONTATO para obter os dados
                        company,
                        creator
                );

                // 2. Monta o SendTemplateMessageRequest para este contato específico
                //    Este DTO vai dentro do payload da fila SQS.
                SendTemplateMessageRequest singleMessageRequest = SendTemplateMessageRequest.builder()
                        .to(contact.getPhoneNumber()) // Usa 'to'
                        .contactId(contact.getId())     // E 'contactId' para rastreamento
                        .templateName(request.getTemplateName())
                        .languageCode(request.getLanguageCode())
                        .resolvedComponents(resolvedComponents) // Passa os componentes JÁ RESOLVIDOS
                        .build();

                // 3. Monta o payload para a fila SQS
                OutgoingMessageRequest queuePayload = OutgoingMessageRequest.builder()
                        .messageType("TEMPLATE")
                        .userId(creator.getId())
                        .templateRequest(singleMessageRequest)
                        .originalRequestId(MDC.get("traceId") + "-bulk-" + contact.getId())
                        .build();
                
                String jsonPayload = objectMapper.writeValueAsString(queuePayload);
                String messageGroupId = "company-" + company.getId();

                sqsTemplate.send(to -> to.queue(outgoingQueueName).payload(jsonPayload).header("message-group-id", messageGroupId));
                enqueuedCount++;
            } catch (JsonProcessingException e) {
                 log.error("Job {}: Falha CRÍTICA ao serializar mensagem para o contato ID {}: {}", jobId, contact.getId(), e.getMessage());
                 // Não continua se a serialização estiver quebrada
                 break;
            } catch (Exception e) {
                log.error("Job {}: Falha ao enfileirar mensagem para o contato ID {}: {}", jobId, contact.getId(), e.getMessage());
                // Continua para os próximos contatos
                
            }
        }

        log.info("Job {}: {} de {} mensagens foram enfileiradas com sucesso.", jobId, enqueuedCount, contactCount);

        // 4. Retornar o resumo do job iniciado
        return BulkMessageResponse.builder()
                .jobId(jobId)
                .status("QUEUED")
                .estimatedContactCount(contactCount)
                .message(enqueuedCount + " de " + contactCount + " mensagens foram enfileiradas para envio.")
                .build();
    }

    private List<Contact> findTargetContacts(BulkMessageTemplateRequest.Targeting targeting, Company company) {
        // Prioriza a lista explícita de números
        if (targeting.getByPhoneNumbers() != null && !targeting.getByPhoneNumbers().isEmpty()) {
            log.debug("Buscando contatos por lista de números para empresa ID {}", company.getId());
            // Remover duplicatas e normalizar números
            Set<String> normalizedNumbers = targeting.getByPhoneNumbers().stream()
                    .map(num -> num.replaceAll("[^0-9]", "")) // Limpa para apenas números
                    .collect(Collectors.toSet());
            return contactRepository.findByCompanyAndPhoneNumberIn(company, new ArrayList<>(normalizedNumbers)); // Adicionar este método ao repo
        }
        // Senão, usa as tags
        if (targeting.getByTags() != null && !targeting.getByTags().isEmpty()) {
            log.debug("Buscando contatos por tags {} para empresa ID {}", targeting.getByTags(), company.getId());
            return contactRepository.findByCompanyAndTagsNameIn(company, new ArrayList<>(targeting.getByTags())); // Adicionar este método ao repo
        }
        // Se nenhum critério for fornecido, não retorna ninguém
        throw new BusinessException("Critério de segmentação (por tags ou por números) é obrigatório.");
    }
}
