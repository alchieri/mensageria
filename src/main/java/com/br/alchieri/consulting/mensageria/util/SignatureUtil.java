package com.br.alchieri.consulting.mensageria.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SignatureUtil {

    private static final Logger logger = LoggerFactory.getLogger(SignatureUtil.class);
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    @Value("${meta.app.secret}")
    private String appSecret;

    private Mac macInstance;

    @PostConstruct
    private void initializeMac() {
        if (appSecret == null || appSecret.isBlank()) {
            logger.error("!!!!!!!!!! App Secret da Meta (META_APP_SECRET) não está configurado! A verificação de assinatura do Webhook FALHARÁ. !!!!!!!!!");
            // Não inicializa o Mac se o segredo não estiver presente
            return;
        }
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM);
            macInstance = Mac.getInstance(SIGNATURE_ALGORITHM);
            macInstance.init(secretKeySpec);
            logger.info("Algoritmo {} para verificação de Webhook inicializado.", SIGNATURE_ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Falha CRÍTICA ao inicializar o algoritmo {} para verificação de Webhook:", SIGNATURE_ALGORITHM, e);
            // Lançar exceção aqui impede a aplicação de iniciar sem segurança de webhook
            throw new RuntimeException("Falha ao inicializar a verificação de assinatura do webhook", e);
        }
    }

    public boolean verifySignature(String payload, String signatureHeader) {
        if (macInstance == null) {
            logger.error("Verificação de assinatura falhou: Mac não inicializado (App Secret ausente ou inválido?).");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            logger.warn("Formato de assinatura do webhook inválido ou ausente: {}", signatureHeader);
            return false;
        }

        try {
            String expectedHash = signatureHeader.substring(7); // Remove "sha256="
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            // Sincroniza o acesso ao Mac se este utilitário puder ser chamado concorrentemente
            // Embora a instância Mac geralmente seja thread-safe após init, é uma boa prática
            // se houver qualquer dúvida sobre o ambiente de execução.
            byte[] calculatedHashBytes;
            synchronized (macInstance) {
                 calculatedHashBytes = macInstance.doFinal(payloadBytes);
            }

            String calculatedHash = Hex.encodeHexString(calculatedHashBytes);

            logger.debug("Verificando assinatura - Esperado: {}, Calculado: {}", expectedHash, calculatedHash);

            // Comparação segura contra timing attacks
            return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), calculatedHash.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) { // Pode incluir IllegalStateException do Mac não inicializado se a lógica PostConstruct falhar
            logger.error("Erro durante a verificação da assinatura do webhook:", e);
            return false;
        }
    }
}
