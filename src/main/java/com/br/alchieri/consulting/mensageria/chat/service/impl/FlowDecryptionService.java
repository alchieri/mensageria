package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FlowDecryptionService {

    private final PrivateKey privateKey;

    private final ObjectMapper objectMapper;

    public FlowDecryptionService(
            @Value("${whatsapp.flow.private-key.s3-bucket}") String s3Bucket,
            @Value("${whatsapp.flow.private-key.s3-key}") String s3Key,
            S3Template s3Template,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        Security.addProvider(new BouncyCastleProvider());
        
        // 1. Carrega o conteúdo da chave privada do S3
        String privateKeyPem = loadPrivateKeyFromS3(s3Bucket, s3Key, s3Template);
        
        // 2. Processa o conteúdo PEM para criar o objeto PrivateKey
        this.privateKey = parsePrivateKey(privateKeyPem);
    }

    /**
     * Baixa o conteúdo do arquivo da chave privada do S3.
     */
    private String loadPrivateKeyFromS3(String bucket, String key, S3Template s3Template) {
        log.info("Carregando chave privada do Flow do S3: s3://{}/{}", bucket, key);
        try {
            // S3Template pode baixar um objeto como um Spring Resource
            Resource s3Resource = s3Template.download(bucket, key);
            // Lê o conteúdo do Resource para uma String
            return s3Resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("FALHA CRÍTICA ao carregar a chave privada do S3 (s3://{}/{}). A descriptografia do Flow não funcionará.", bucket, key, e);
            throw new RuntimeException("Não foi possível carregar a chave privada do S3.", e);
        }
    }

    /**
     * Faz o parse do conteúdo PEM da chave para um objeto PrivateKey.
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            log.error("!!!!!!!!!! Conteúdo da chave privada do Flow está vazio. A descriptografia FALHARÁ. !!!!!!!!!!");
            throw new IllegalStateException("Conteúdo da chave privada do Flow está vazio.");
        }
        try {
            PemReader pemReader = new PemReader(new StringReader(privateKeyPem));
            PemObject pemObject = pemReader.readPemObject();
            pemReader.close();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(content);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        } catch (Exception e) {
            log.error("Erro fatal ao fazer o parse do conteúdo da chave privada do Flow.", e);
            throw new RuntimeException("Não foi possível fazer o parse da chave privada do Flow.", e);
        }
    }

    public JsonNode decryptAndParse(String encryptedFlowDataBase64, byte[] decryptedAesKey, byte[] iv) {
        try {
            log.debug("Descriptografando payload do Flow com chave AES e IV fornecidos.");

            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decryptedAesKey, "AES");
            aesCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec);

            byte[] decryptedFlowData = aesCipher.doFinal(Base64.getDecoder().decode(encryptedFlowDataBase64));
            String jsonResponse = new String(decryptedFlowData, StandardCharsets.UTF_8);
            
            log.debug("Payload do Flow descriptografado: {}", jsonResponse);
            return objectMapper.readTree(jsonResponse);

        } catch (Exception e) {
            log.error("Falha ao descriptografar o payload principal do Flow.", e);
            throw new RuntimeException("Descriptografia do payload do Flow falhou.", e);
        }
    }

    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }
}
