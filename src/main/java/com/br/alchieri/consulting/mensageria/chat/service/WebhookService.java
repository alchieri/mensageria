package com.br.alchieri.consulting.mensageria.chat.service;

public interface WebhookService {

    /**
     * Verifica a assinatura da requisição de webhook usando o App Secret.
     * @param payload Corpo da requisição (raw).
     * @param signatureHeader Valor do header X-Hub-Signature-256.
     * @return true se a assinatura for válida, false caso contrário.
     */
    boolean verifySignature(String payload, String signatureHeader);

    void queueWebhookEvent(String payload);

    /**
     * Processa o payload recebido do webhook do WhatsApp.
     * Deserializa e direciona para a lógica apropriada (mensagem recebida, status, etc.).
     * @param payload Corpo da requisição (raw JSON string).
     */
    void processWebhookPayload(String payload);

    /**
     * Verifica a assinatura e enfileira o payload bruto do webhook para processamento assíncrono.
     * @param payload A string JSON bruta da Meta.
     * @param signature O header de assinatura.
     */
    void queueWebhookEvent(String payload, String signature);

    void processWebhookPayload(String payload, String signature);
}
