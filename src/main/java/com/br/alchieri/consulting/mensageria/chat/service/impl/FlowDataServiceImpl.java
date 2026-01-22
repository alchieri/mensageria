package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.io.ByteArrayInputStream;
import java.security.spec.MGF1ParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowDataRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.MediaUploadRepository;
import com.br.alchieri.consulting.mensageria.chat.service.CallbackService;
import com.br.alchieri.consulting.mensageria.chat.service.FlowDataService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SignatureUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowDataServiceImpl implements FlowDataService {

    private final SignatureUtil signatureUtil;
    private final FlowDecryptionService decryptionService;
    private final FlowEncryptionService encryptionService;
    private final FlowMediaDecryptionService mediaDecryptionService;
    private final ObjectMapper objectMapper;
    private final FlowDataRepository flowDataRepository;
    private final FlowRepository flowRepository;
    private final ContactRepository contactRepository;
    private final MediaUploadRepository mediaUploadRepository;
    private final S3Template s3Template;
    private final CallbackService callbackService;

    @Value("${aws.s3.media-bucket-name}")
    private String s3MediaBucketName;

    @Override
    @Transactional
    public String processEncryptedFlowData(String encryptedBody, String signature) {
        
        if (!signatureUtil.verifySignature(encryptedBody, signature)) {
            throw new SecurityException("Assinatura do payload inválida.");
        }
        log.info("Assinatura do Flow Endpoint verificada com sucesso.");

        try {
            // 1. Extrair as strings Base64
            JsonNode rootNode = objectMapper.readTree(encryptedBody);
            String encryptedFlowDataBase64 = extractBase64String(rootNode.path("encrypted_flow_data"), "encrypted_flow_data");
            String encryptedAesKeyBase64 = extractBase64String(rootNode.path("encrypted_aes_key"), "encrypted_aes_key");
            String ivBase64 = extractBase64String(rootNode.path("initial_vector"), "initial_vector");

            if (encryptedFlowDataBase64 == null || encryptedAesKeyBase64 == null || ivBase64 == null) {
                throw new BusinessException("Payload criptografado incompleto ou inválido.");
            }

            // 2. Decodificar o IV (permanece em uma variável local)
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            // 3. Descriptografar a chave AES (lógica agora está AQUI)
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            rsaCipher.init(Cipher.DECRYPT_MODE, decryptionService.getPrivateKey(), oaepParams);
            byte[] decryptedAesKey = rsaCipher.doFinal(Base64.getDecoder().decode(encryptedAesKeyBase64));

            // 4. Chamar o serviço para descriptografar APENAS o payload principal
            JsonNode decryptedNode = decryptionService.decryptAndParse(
                    encryptedFlowDataBase64,
                    decryptedAesKey, // <<< Passa a chave já pronta
                    iv
            );

            String action = decryptedNode.path("action").asText();

            // Ações de sistema são tratadas e retornam imediatamente.
            if ("ping".equals(action) || "error".equals(action)) {
                log.info("Processando ação de sistema do Flow: '{}'", action);
                ObjectNode responsePayload = buildGenericResponsePayload(decryptedNode);
                return encryptionService.encryptResponse(responsePayload, decryptedAesKey, iv);
            }

            // 5. Salvar os dados e processar mídias
            saveFlowDataAndProcessMedia(decryptedNode);

            // 6. Construir a resposta
            ObjectNode responsePayload = buildGenericResponsePayload(decryptedNode);
            
            // 7. Criptografar a resposta
            log.info("Criptografando resposta para o Flow. Dados: {}", responsePayload);
            String encryptedResponseData = encryptionService.encryptResponse(
                    responsePayload,
                    decryptedAesKey, // <<< Usa a chave que descriptografamos
                    iv               // <<< Usa o IV original
            );
            
            // 8. Retornar a string criptografada
            return encryptedResponseData;

        } catch (Exception e) {
            log.error("Erro inesperado ao processar dados do Flow.", e);
            // Relança como RuntimeException para o controller pegar e retornar 500
            throw new RuntimeException("Falha inesperada no processamento dos dados do Flow.", e);
        }
    }

    @Override
    public Page<FlowData> getFlowDataByFlowId(Long flowId, Company company, Pageable pageable) {
        
        return flowDataRepository.findByFlowIdAndCompany(flowId, company, pageable);
    }

    /**
     * Constrói uma resposta genérica para o WhatsApp Flow, focando na navegação e
     * no repasse de dados, sem interpretar a lógica de negócio.
     */
    private ObjectNode buildGenericResponsePayload(JsonNode decryptedNode) {
        
        ObjectNode responsePayload = objectMapper.createObjectNode();
        String action = decryptedNode.path("action").asText();
        JsonNode dataFromRequest = decryptedNode.path("data");
        String flowToken = decryptedNode.path("flow_token").asText();

        responsePayload.put("version", "3.0"); // Manter a versão consistente
        ObjectNode responseData = objectMapper.createObjectNode();

        switch (action) {
            case "INIT":
            case "BACK":
                // Para INIT e BACK, simplesmente navegamos para a próxima tela
                // definida no payload, se houver.
                responsePayload.put("screen", dataFromRequest.path("next_screen").asText("DEFAULT_SCREEN"));
                // Repassamos quaisquer dados que o Flow tenha enviado para a próxima tela
                responseData.setAll((ObjectNode) dataFromRequest);
                break;

            case "data_exchange":
                // Pega o nome da próxima tela do payload da requisição.
                String nextScreen = dataFromRequest.path("next_screen").asText(null);

                if (nextScreen != null) {
                    // Se uma próxima tela foi especificada, navegamos para ela
                    responsePayload.put("screen", nextScreen);
                    // E repassamos todos os dados recebidos para a próxima tela
                    responseData.setAll((ObjectNode) dataFromRequest);
                } else {
                    // Se não houver 'next_screen', assumimos que é o fim do Flow
                    responsePayload.put("screen", "SUCCESS");
                    
                    ObjectNode extensionParams = objectMapper.createObjectNode();
                    extensionParams.put("flow_token", flowToken);

                    String metaFlowId = decryptedNode.path("flow_id").asText(null);
                    if (metaFlowId != null) {
                        extensionParams.put("flow_id", metaFlowId);
                    }

                    extensionParams.set("flow_data", dataFromRequest);
                    
                    ObjectNode extensionResponse = objectMapper.createObjectNode();
                    extensionResponse.set("params", extensionParams);
                    responseData.set("extension_message_response", extensionResponse);
                }
                break;
            
            case "ping":
                log.info("Recebido Health Check (ping) da Meta. Respondendo 'active'.");
                responseData.put("status", "active");
                break;
            
            case "error":
                responseData.put("acknowledged", true);
                break;

            default:
                log.error("Ação de Flow desconhecida recebida: {}", action);
                responseData.put("error_message", "Ação desconhecida recebida pelo servidor.");
                // Fica na tela atual em caso de erro
                responsePayload.put("screen", decryptedNode.path("screen").asText());
                break;
        }

        responsePayload.set("data", responseData);
        return responsePayload;
    }

    private void saveFlowDataAndProcessMedia(JsonNode decryptedNode) {
        String flowIdFromMeta = decryptedNode.path("flow_id").asText(null);
        String userWaId = decryptedNode.path("user_wa_id").asText(null);
        JsonNode dataNode = decryptedNode.path("data");

        // Validação Essencial: Garante que o identificador mínimo existe.
        if (!StringUtils.hasText(userWaId)) {
            log.error("Payload descriptografado do Flow não contém 'user_wa_id'. Não é possível salvar. Payload: {}", decryptedNode);
            throw new BusinessException("Dados essenciais (user_wa_id) ausentes no payload do Flow.");
        }

        // 1. Tenta encontrar as entidades correspondentes no nosso sistema (sem lançar exceção se não encontrar)
        Flow flow = (flowIdFromMeta != null) ? flowRepository.findByMetaFlowId(flowIdFromMeta).orElse(null) : null;
        
        Company company = (flow != null) ? flow.getCompany() : null;
        
        Contact contact = null;
        if (company != null) {
            contact = contactRepository.findByCompanyAndPhoneNumber(company, userWaId).orElse(null);
        }

        if (flow == null) {
            log.warn("Não foi possível associar a resposta do Flow a uma entidade Flow local. O Flow ID da Meta '{}' não foi encontrado em nosso sistema.", flowIdFromMeta);
        }
        if (company == null && flow != null) {
            log.warn("Flow ID {} encontrado, mas não está associado a nenhuma empresa.", flow.getId());
        }
        if (contact == null && company != null) {
            log.info("Nenhum contato correspondente ao WA ID '{}' encontrado para a empresa ID {}.", userWaId, company.getId());
        }

        // 2. Cria e salva o registro FlowData (agora mais resiliente)
        FlowData flowData = new FlowData();
        flowData.setCompany(company); // Será nulo se o flow não foi encontrado
        flowData.setContact(contact); // Será nulo se o contato não foi encontrado
        flowData.setFlow(flow);       // Será nulo se o flow não foi encontrado
        flowData.setSenderWaId(userWaId); // Garantido que não é nulo pela verificação no início
        try {
            flowData.setDecryptedJsonResponse(objectMapper.writeValueAsString(dataNode));
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar dados do Flow para o banco.", e);
            flowData.setDecryptedJsonResponse("{\"error\":\"serialization_failed\"}");
        }
        FlowData savedFlowData = flowDataRepository.save(flowData);
        log.info("Dados de texto do Flow salvos no banco com ID {}. Associado à Empresa: {}, Contato: {}",
                 savedFlowData.getId(),
                 company != null ? company.getId() : "N/A",
                 contact != null ? contact.getId() : "N/A");

        // 3. Dispara o processamento de mídias e o callback para o cliente
        // Esses métodos já verificam se 'company' é nulo antes de prosseguir.
        User userForMediaUpload = (contact != null && contact.getCompany().getUsers().getFirst() != null) ? contact.getCompany().getUsers().getFirst() :
                                  (company != null ? company.getUsers().stream().findFirst().orElse(null) : null);

        processFlowMedia(decryptedNode, flow, company, userForMediaUpload)
            .doOnError(e -> log.error("Erro no pipeline de processamento assíncrono de mídias do Flow: {}", e.getMessage()))
            .subscribe();

        if (company != null) {
            callbackService.sendFlowDataCallback(company.getId(), savedFlowData.getId());
        }
    }

    private Mono<Void> processFlowMedia(JsonNode decryptedNode, Flow flow, Company company, User user) {
        List<Mono<Void>> mediaProcessingMonos = new ArrayList<>();

        if (user == null) {
            log.warn("Nenhum usuário encontrado na Empresa ID {} para associar ao upload da mídia. O upload será cancelado.", company.getId());
            return Mono.error(new BusinessException("Nenhum usuário encontrado na empresa para atribuir o upload da mídia."));
        }

        if (flow == null || company == null) {
            log.debug("Pulando processamento de mídia pois o Flow ou a Empresa não puderam ser identificados.");
            return Mono.empty();
        }

        // Itera sobre os campos dentro do objeto 'data'
        decryptedNode.path("data").fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            // Campos de Picker (PhotoPicker, DocumentPicker) são arrays de objetos
            if (fieldValue.isArray()) {
                fieldValue.forEach(mediaNode -> {
                    // Verifica se o objeto dentro do array tem a estrutura de um item de mídia
                    if (mediaNode.has("media_id") && mediaNode.has("encryption_metadata")) {
                        log.info("Campo de mídia '{}' detectado para processamento.", fieldName);
                        Mono<Void> processingMono = processSingleMediaItem(mediaNode, flow, company, user);
                        mediaProcessingMonos.add(processingMono);
                    }
                });
            }
        });

        if (mediaProcessingMonos.isEmpty()) {
            return Mono.empty();
        }

        // Executa todos os Monos de processamento em paralelo e retorna um Mono<Void>
        // que completa quando TODOS eles tiverem completado.
        return Flux.merge(mediaProcessingMonos).then();
    }

    private Mono<Void> processSingleMediaItem(JsonNode mediaNode, Flow flow, Company company, User user) {
        String metaMediaId = mediaNode.path("media_id").asText();
        String cdnUrl = mediaNode.path("cdn_url").asText();
        String fileName = mediaNode.path("file_name").asText("unknown_file");

        JsonNode cryptoMeta = mediaNode.path("encryption_metadata");
        String encryptedHash = cryptoMeta.path("encrypted_hash").asText();
        String iv = cryptoMeta.path("iv").asText();
        String encKey = cryptoMeta.path("encryption_key").asText();
        String hmacKey = cryptoMeta.path("hmac_key").asText();
        String plaintextHash = cryptoMeta.path("plaintext_hash").asText();

        // Determina o usuário que fez o upload
        final User uploader = (user != null) ? user : company.getUsers().stream().findFirst().orElse(null);

        if (uploader == null) {
            log.warn("Nenhum usuário encontrado para a Empresa ID {} para associar ao upload da mídia {}. O upload será cancelado.",
                     company.getId(), metaMediaId);
            return Mono.error(new BusinessException("Nenhum usuário encontrado na empresa para atribuir o upload da mídia."));
        }

        // 1. Descriptografa a mídia
        return mediaDecryptionService.downloadAndDecrypt(cdnUrl, encryptedHash, iv, encKey, hmacKey,
                                                         plaintextHash, decryptionService.getPrivateKey())
            // 2. Com os bytes descriptografados, inicia a próxima etapa
            .flatMap(decryptedBytes -> {
                String objectKey = String.format("company-%d/flow-media/%s-%s", company.getId(), UUID.randomUUID(), fileName);
                log.debug("Mídia descriptografada, fazendo upload para S3 com a chave: {}", objectKey);
                
                // Envolve as operações bloqueantes (upload S3 + save DB) em um Mono
                return Mono.fromRunnable(() -> {
                    // 2a. Operação de Upload S3
                    s3Template.upload(s3MediaBucketName, objectKey, new ByteArrayInputStream(decryptedBytes));

                    // 2b. Operação de salvamento no Banco de Dados
                    MediaUpload mediaUpload = new MediaUpload();
                    mediaUpload.setCompany(company);
                    mediaUpload.setUploadedBy(uploader);
                    mediaUpload.setMetaMediaId(metaMediaId);
                    mediaUpload.setOriginalFilename(fileName);
                    mediaUpload.setContentType(detectMimeType(fileName));
                    mediaUpload.setFileSize((long) decryptedBytes.length);
                    mediaUpload.setS3BucketName(s3MediaBucketName);
                    mediaUpload.setS3ObjectKey(objectKey);

                    mediaUploadRepository.save(mediaUpload);
                    log.info("Mídia do Flow (Meta ID {}) salva no S3 e no banco com a chave {}", metaMediaId, objectKey);
                })
                .subscribeOn(Schedulers.boundedElastic()) // Executa o Runnable em uma thread de I/O
                .then(); // Converte o Mono<Void> do fromRunnable para o tipo de retorno do flatMap
            });
    }

    private String detectMimeType(String fileName) {
        try {
            String extension = StringUtils.getFilenameExtension(fileName);
            if (extension != null) {
                return MediaTypeFactory.getMediaType("file." + extension)
                                       .orElse(MediaType.APPLICATION_OCTET_STREAM)
                                       .toString();
            }
        } catch (Exception e) {
            log.warn("Não foi possível detectar o MediaType para o arquivo {}", fileName);
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * Novo método helper para extrair a string Base64 de um JsonNode,
     * lidando com o caso de ser uma string direta ou um array de uma única string.
     */
    private String extractBase64String(JsonNode node, String fieldName) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray() && node.size() > 0 && node.get(0).isTextual()) {
            log.warn("O campo '{}' foi recebido como um array, extraindo o primeiro elemento.", fieldName);
            return node.get(0).asText();
        } else {
            log.error("O campo '{}' não é uma string válida ou um array de string. Valor: {}", fieldName, node.toString());
            return null;
        }
    }
}
