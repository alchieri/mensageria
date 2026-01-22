package com.br.alchieri.consulting.mensageria.chat.consumer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor // Usar RequiredArgsConstructor para injeção final
public class WhatsAppMessageConsumer {

    @Lazy
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    private final UserRepository userRepository;

    @Qualifier("metaApiRateLimiterBucket") // Injeta o bucket específico que criamos
    private final Bucket metaApiRateLimiterBucket;

    // Timeout para chamadas bloqueantes dentro do listener
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);

    @SqsListener(value = "${sqs.queue.outgoing}")
    @Transactional
    public void receiveOutgoingMessage(@Payload OutgoingMessageRequest message) {

        // Consome 1 token do bucket. tryConsume retorna true se conseguiu, false caso contrário.
        // Não bloqueia a thread se não houver tokens.
        ConsumptionProbe probe = metaApiRateLimiterBucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long waitForRefillNanos = probe.getNanosToWaitForRefill();
            // Lança exceção para reenfileirar, indicando o tempo estimado de espera
            log.warn("Rate limit Bucket4j atingido. Mensagem voltará para a fila SQS (espera estimada: {} ms). TraceID: {}",
                     TimeUnit.NANOSECONDS.toMillis(waitForRefillNanos), message.getOriginalRequestId());
            throw new RuntimeException("Rate limit hit (Bucket4j), allowing SQS to redrive message.");
        }

        try (@SuppressWarnings("unused")
            MDC.MDCCloseable closable = MDC.putCloseable("traceId", message.getOriginalRequestId() != null ? message.getOriginalRequestId() : "consumer-" + System.nanoTime());
            @SuppressWarnings("unused")
            MDC.MDCCloseable closable2 = MDC.putCloseable("userId", String.valueOf(message.getUserId()))) {

            log.info("Processando mensagem SQS solicitada pelo Usuário ID: {}", message.getUserId());

            User user = userRepository.findById(message.getUserId())
                    .orElseThrow(() -> {
                        log.error("Usuário com ID {} da mensagem SQS não encontrado. Descartando mensagem.", message.getUserId());
                        return new BusinessException("Usuário solicitante (" + message.getUserId() + ") não encontrado.");
                    });

            try {

                log.debug("CONSUMER DA SQS: Empresa ID {}: Enviando payload para Meta API: {}", user.getCompany().getId(), message);
                // Delega o payload inteiro para o serviço, que contém a lógica de envio
                whatsAppCloudApiService.sendFromQueue(message, user).block(API_CALL_TIMEOUT);

                String recipientInfo = "N/A";
                if (message.getTextRequest() != null) {
                    recipientInfo = message.getTextRequest().getTo();
                } else if (message.getTemplateRequest() != null) {
                    recipientInfo = message.getTemplateRequest().getTo();
                } else if (message.getInteractiveFlowRequest() != null) {
                    recipientInfo = message.getInteractiveFlowRequest().getTo();
                }

                log.info("Mensagem SQS para {} (solicitada por Usuário ID {}) processada com sucesso pela API Meta.", recipientInfo, user.getId());

            } catch (WebClientResponseException e) {
                log.warn("WebClientResponseException no SQS Consumer. O log de falha já foi salvo pelo serviço. Status={}", e.getStatusCode());
                if (e.getStatusCode().is5xxServerError() || List.of(408, 429, 503, 504).contains(e.getStatusCode().value())) {
                    log.warn("Erro recuperável ({}), lançando exceção para retentativa SQS.", e.getStatusCode());
                    throw new RuntimeException("Erro recuperável da API Meta (" + e.getStatusCode() + "), permitindo retentativa SQS.", e);
                } else {
                    log.error("Erro NÃO recuperável ({}) da API Meta. Mensagem NÃO será reenfileirada (ACK).", e.getStatusCode());
                    log.error("Detalhes do erro: {}", e.getResponseBodyAsString());
                    // Não lançar exceção, pois o serviço já logou a falha no banco.
                }
            } catch (BusinessException | IllegalStateException ise) {
                log.error("Erro de negócio ou estado ilegal (ex: timeout) no SQS Consumer: {}", ise.getMessage());
                throw new RuntimeException("Erro de negócio/timeout no processamento SQS.", ise); // Reenfileira
            } catch (Exception e) {
                log.error("Erro inesperado no SQS Consumer: {}", e.getMessage(), e);
                throw new RuntimeException("Erro inesperado no processamento da mensagem SQS.", e); // Reenfileira
            }

        } catch (BusinessException be) {
            // Captura o erro se o usuário não for encontrado e evita que a mensagem seja reenfileirada.
            log.error("Erro de negócio irrecuperável ao processar mensagem SQS. A mensagem será descartada (ACK). Causa: {}", be.getMessage());
            // Não relança a exceção para que o listener considere a mensagem "processada".
        } catch (Exception e) {
             log.error("Erro fatal ao tentar processar mensagem SQS. Verifique a causa. Mensagem: {}", message, e);
             throw e; // Reenfileira / DLQ
        }
    }
}
