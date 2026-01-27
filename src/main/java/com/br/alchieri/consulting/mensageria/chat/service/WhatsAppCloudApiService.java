package com.br.alchieri.consulting.mensageria.chat.service;

import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMediaMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMultiProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.MessageStatusResponse;
import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.model.User;

import reactor.core.publisher.Mono;

public interface WhatsAppCloudApiService {

    Mono<Void> sendFromQueue(OutgoingMessageRequest queueRequest, User user);

    Mono<Void> sendTextMessage(SendTextMessageRequest request, User user);
    Mono<Void> sendTemplateMessage(SendTemplateMessageRequest request, User user, Long scheduledMessageId);
    Mono<Void> sendInteractiveFlowMessage(SendInteractiveFlowMessageRequest request, User user);

    Mono<MediaUpload> uploadMedia(MultipartFile file, String messagingProduct, User user, String phoneNumberId);

    Mono<Void> sendMediaMessage(SendMediaMessageRequest request, User user);

    Mono<MessageStatusResponse> getMessageStatusByWamid(String wamid);
    Mono<MessageStatusResponse> getMessageStatusByLogId(Long logId);

    Mono<Void> sendProductMessage(SendProductMessageRequest request, User user);
    Mono<Void> sendMultiProductMessage(SendMultiProductMessageRequest request, User user);

    // Adicionar métodos para enviar mídia avulsa (não template), etc., conforme necessário
    // Mono<Void> sendMediaMessage(SendMediaMessageRequest request);
}
