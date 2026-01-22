package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.security.MessageDigest;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowMediaDecryptionService {

    private final WebClient.Builder webClientBuilder;

    static {
        // Garante que o Bouncy Castle provider está carregado estaticamente
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Baixa, valida e descriptografa um arquivo de mídia enviado por um WhatsApp Flow.
     * @param cdnUrl A URL do CDN fornecida no payload do Flow.
     * @param encryptedHash O hash SHA256 do arquivo criptografado (fornecido no payload).
     * @param ivBase64 O vetor de inicialização em Base64 (fornecido no payload).
     * @param encryptionKeyBase64 A chave AES (criptografada com RSA) em Base64 (fornecida no payload).
     * @param hmacKeyBase64 A chave HMAC em Base64 (fornecida no payload).
     * @param plaintextHash O hash SHA256 esperado do arquivo final descriptografado (fornecido no payload).
     * @param privateKey A chave privada RSA da sua aplicação para descriptografar a chave AES.
     * @return Um Mono contendo o array de bytes do arquivo descriptografado.
     */
    public Mono<byte[]> downloadAndDecrypt(String cdnUrl, String encryptedHash, String ivBase64,
                                           String encryptionKeyBase64, String hmacKeyBase64,
                                           String plaintextHash, java.security.PrivateKey privateKey) {

        WebClient webClient = webClientBuilder.build();

        // 1. Baixar o arquivo criptografado do CDN
        return webClient.get().uri(cdnUrl).retrieve().bodyToMono(byte[].class)
            .flatMap(cdnFile -> Mono.fromCallable(() -> {
                // Etapas de validação e descriptografia (síncronas)
                
                // 2. Validar o hash do arquivo baixado
                byte[] calculatedEncHash = MessageDigest.getInstance("SHA-256").digest(cdnFile);
                if (!encryptedHash.equals(Base64.getEncoder().encodeToString(calculatedEncHash))) {
                    throw new SecurityException("Falha na validação do hash do arquivo criptografado.");
                }
                log.debug("Hash do arquivo CDN validado com sucesso.");

                // Separa o ciphertext e o hmac10
                if (cdnFile.length < 10) {
                    throw new IllegalArgumentException("Arquivo do CDN é muito pequeno.");
                }
                byte[] ciphertext = Arrays.copyOfRange(cdnFile, 0, cdnFile.length - 10);
                byte[] hmac10 = Arrays.copyOfRange(cdnFile, cdnFile.length - 10, cdnFile.length);

                // Descriptografa a chave HMAC
                byte[] hmacKey = decryptRsaKey(hmacKeyBase64, privateKey);
                
                // 3. Validar o HMAC do ciphertext
                Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                hmacSha256.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
                hmacSha256.update(Base64.getDecoder().decode(ivBase64)); // Adiciona o IV ao HMAC
                byte[] calculatedHmac = hmacSha256.doFinal(ciphertext);
                byte[] calculatedHmac10 = Arrays.copyOf(calculatedHmac, 10);

                if (!Arrays.equals(hmac10, calculatedHmac10)) {
                    throw new SecurityException("Falha na validação do HMAC.");
                }
                log.debug("HMAC do ciphertext validado com sucesso.");

                // Descriptografa a chave AES
                byte[] aesKey = decryptRsaKey(encryptionKeyBase64, privateKey);

                // 4. Descriptografar o conteúdo
                Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
                aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(Base64.getDecoder().decode(ivBase64)));
                byte[] decryptedMedia = aesCipher.doFinal(ciphertext);
                log.debug("Mídia descriptografada com sucesso (tamanho: {} bytes).", decryptedMedia.length);

                // 5. Validar o hash do conteúdo descriptografado
                byte[] calculatedPlaintextHash = MessageDigest.getInstance("SHA-256").digest(decryptedMedia);
                if (!plaintextHash.equals(Base64.getEncoder().encodeToString(calculatedPlaintextHash))) {
                    throw new SecurityException("Falha na validação do hash do arquivo descriptografado.");
                }
                log.info("Hash do arquivo final validado com sucesso. A mídia é autêntica.");

                return decryptedMedia;

            }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * Descriptografa uma chave (AES ou HMAC) que foi criptografada com RSA.
     */
    private byte[] decryptRsaKey(String encryptedKeyBase64, java.security.PrivateKey privateKey) throws Exception {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return rsaCipher.doFinal(Base64.getDecoder().decode(encryptedKeyBase64));
    }
}
