package com.br.alchieri.consulting.mensageria.chat.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.chat.service.FlowDataService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/flow-data")
@Slf4j
@RequiredArgsConstructor
public class FlowDataController {

    private final FlowDataService flowDataService;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    @PostMapping("/receive")
    public ResponseEntity<?> receiveFlowData(
            @RequestBody String encryptedBody,
            @RequestHeader("x-hub-signature-256") String signature) {

        try {
            // O serviço agora retorna a string Base64 da resposta criptografada
            String encryptedResponse = flowDataService.processEncryptedFlowData(encryptedBody, signature);
            
            log.info("Enviando resposta criptografada (text/plain) de volta para o WhatsApp Flow.");
            // Retorna a string Base64 como texto puro, conforme a documentação do Flow Endpoint
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(encryptedResponse);

        } catch (SecurityException e) {
            // Este erro ocorre se a assinatura HMAC (X-Hub-Signature-256) for inválida.
            log.warn("Falha na verificação da assinatura do Flow Endpoint: {}", e.getMessage());
            // Retorna 403 Forbidden. O WhatsApp Client pode tentar novamente ou mostrar um erro.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Signature verification failed."); // Corpo de erro simples
        } catch (BusinessException e) {
             // Captura erros de negócio (ex: payload incompleto, dados inválidos).
             // A Meta recomenda retornar 421 Misdirected Request para erros de descriptografia,
             // mas 400 é um padrão mais comum e compreensível para dados malformados.
             log.warn("Erro de negócio ao processar dados do Flow: {}", e.getMessage());
             
             // A API não espera um corpo de erro JSON complexo, mas um status code claro.
             // Retornar um corpo de erro simples em texto pode ajudar na depuração.
             return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error processing request: " + e.getMessage());
        } catch (Exception e) {
            // Captura todas as outras exceções (ex: falha na descriptografia, erro de banco de dados).
            // Estes são erros do servidor.
            log.error("Erro interno inesperado ao processar dados do Flow.", e);
            
            // Retorna 500 Internal Server Error. O WhatsApp Client pode tentar novamente.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Internal server error while processing flow data.");
        }
    }

    @GetMapping("/flow/{flowId}")
    @Operation(summary = "Listar Respostas de um Flow", description = "Retorna os dados preenchidos (Flow Data) vinculados a um Flow específico, com paginação.")
    public ResponseEntity<Page<FlowDataResponseDTO>> getFlowDataByFlow(
            @PathVariable Long flowId,
            @PageableDefault(sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User currentUser = securityUtils.getAuthenticatedUser();
        Company company = currentUser.getCompany(); // Garante que só busca dados da empresa do usuário

        Page<FlowData> flowDataPage = flowDataService.getFlowDataByFlowId(flowId, company, pageable);

        // Converte a entidade para um DTO onde o JSON já vai parseado (objeto) e não string
        Page<FlowDataResponseDTO> dtoPage = flowDataPage.map(this::convertToDto);

        return ResponseEntity.ok(dtoPage);
    }

    private FlowDataResponseDTO convertToDto(FlowData entity) {
        FlowDataResponseDTO dto = new FlowDataResponseDTO();
        dto.setId(entity.getId());
        dto.setSenderWaId(entity.getSenderWaId());
        dto.setReceivedAt(entity.getReceivedAt().toString());
        dto.setContactName(entity.getContact() != null ? entity.getContact().getName() : "Desconhecido");
        
        try {
            // Converte a String JSON salva no banco de volta para um Objeto Java (Map)
            // Isso faz com que no JSON de resposta da API venha como um objeto estruturado
            if (entity.getDecryptedJsonResponse() != null) {
                dto.setResponseData(objectMapper.readValue(entity.getDecryptedJsonResponse(), Map.class));
            }
        } catch (JsonProcessingException e) {
            dto.setResponseData(Map.of("error", "Invalid JSON structure", "raw", entity.getDecryptedJsonResponse()));
        }
        
        return dto;
    }

    // DTO interno simples para resposta
    @Data
    public static class FlowDataResponseDTO {
        private Long id;
        private String senderWaId;
        private String contactName;
        private String receivedAt;
        private Map<String, Object> responseData; // O JSON do formulário
    }
}
