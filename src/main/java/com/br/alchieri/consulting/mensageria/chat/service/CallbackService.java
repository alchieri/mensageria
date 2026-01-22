package com.br.alchieri.consulting.mensageria.chat.service;

public interface CallbackService {

    /** Envia notificação de mensagem recebida (iniciada pelo usuário). */
    void sendIncomingMessageCallback(Long companyId, Long messageLogId);

    /** Envia notificação de atualização de status de mensagem enviada (sent, delivered, read, failed). */
    void sendStatusCallback(Long companyId, Long messageLogId);

    /** Envia notificação de atualização de status de template (APPROVED, REJECTED). */
    void sendTemplateStatusCallback(Long companyId, Long clientTemplateId);
    
    /** Envia notificação de atualização de status de campanha (PROCESSING, COMPLETED, CANCELED). */
    void sendCampaignStatusCallback(Long companyId, Long campaignId);

    /** Envia notificação de atualização de status de Flow (PUBLISHED, DEPRECATED, DISABLED). */
    void sendFlowStatusCallback(Long companyId, Long flowId);
    
    /** Envia os dados descriptografados recebidos de um Flow Endpoint (Data Exchange). */
    void sendFlowDataCallback(Long companyId, Long flowDataId);
}
