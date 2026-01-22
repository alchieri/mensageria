package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowEncryptionService {

    private final ObjectMapper objectMapper;

    /**
     * Criptografa um payload de resposta para ser enviado de volta ao WhatsApp Flow.
     * @param responsePayload O objeto (ex: Map ou DTO) a ser enviado como dados para a próxima tela.
     * @param decryptedAesKey A chave AES (já descriptografada da requisição).
     * @param requestIv O Vetor de Inicialização original da requisição.
     * @return A string Base64 do payload criptografado.
     */
    public String encryptResponse(Object responsePayload, byte[] decryptedAesKey, byte[] requestIv) {
        try {
            // 1. Inverter o IV da requisição para criar o IV da resposta
            byte[] responseIv = flipIv(requestIv);

            // 2. Criptografar o payload JSON com a chave AES e o IV invertido
            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, responseIv);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decryptedAesKey, "AES");
            aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);

            String jsonPayload = objectMapper.writeValueAsString(responsePayload);
            byte[] encryptedData = aesCipher.doFinal(jsonPayload.getBytes(StandardCharsets.UTF_8));
            
            // 3. Codifica o resultado em Base64 e retorna
            // A resposta é apenas o corpo criptografado em Base64, não um objeto JSON
            return Base64.getEncoder().encodeToString(encryptedData);

        } catch (Exception e) {
            log.error("Falha ao criptografar a resposta do Flow.", e);
            throw new RuntimeException("Criptografia da resposta do Flow falhou.", e);
        }
    }

    /**
     * Inverte todos os bits de um array de bytes.
     * Usado para gerar o IV da resposta a partir do IV da requisição.
     */
    private byte[] flipIv(final byte[] iv) {
        final byte[] result = new byte[iv.length];
        for (int i = 0; i < iv.length; i++) {
            result[i] = (byte) (iv[i] ^ 0xFF);
        }
        return result;
    }
}
