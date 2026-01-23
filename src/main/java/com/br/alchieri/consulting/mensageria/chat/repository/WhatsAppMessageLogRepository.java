package com.br.alchieri.consulting.mensageria.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLog, Long> {

    Optional<WhatsAppMessageLog> findByWamid(String wamid);

    boolean existsByWamid(String wamid);

    @Query("SELECT log FROM WhatsAppMessageLog log LEFT JOIN FETCH log.company WHERE log.id = :id")
    Optional<WhatsAppMessageLog> findByIdWithCompany(@Param("id") Long id);

    /**
     * Encontra todos os logs de mensagem para uma empresa específica onde o número de telefone fornecido
     * é o remetente ou o destinatário.
     * A ordenação já pode ser definida no Pageable que é passado.
     *
     * @param company A empresa dona dos logs.
     * @param phoneNumber O número de telefone do contato.
     * @param pageable Objeto de paginação e ordenação.
     * @return Uma página de logs de mensagem.
     */
    @Query("SELECT log FROM WhatsAppMessageLog log " +
           "WHERE log.company = :company AND (log.sender = :phoneNumber OR log.recipient = :phoneNumber)")
    Page<WhatsAppMessageLog> findByCompanyAndPhoneNumber(
            @Param("company") Company company,
            @Param("phoneNumber") String phoneNumber,
            Pageable pageable);

       /**
     * Encontra a última mensagem para cada conversa (definida pelo par sender/recipient)
     * que teve uma mensagem recebida (INCOMING) dentro do período de tempo especificado.
     * Esta é uma query complexa e pode precisar de otimização dependendo do SGBD.
     * JPQL não suporta subqueries na cláusula FROM ou window functions facilmente,
     * então uma query nativa pode ser mais performática, mas JPQL pode ser suficiente.
     *
     * Esta query busca os IDs das últimas mensagens de cada conversa ativa.
     */
    @Query("SELECT log.id FROM WhatsAppMessageLog log " +
           "WHERE log.company = :company " +
           "AND log.createdAt = (SELECT MAX(subLog.createdAt) FROM WhatsAppMessageLog subLog " +
           "                     WHERE subLog.company = :company " +
           "                     AND ((subLog.sender = log.sender AND subLog.recipient = log.recipient) OR (subLog.sender = log.recipient AND subLog.recipient = log.sender))) " +
           "AND EXISTS (SELECT 1 FROM WhatsAppMessageLog incomingCheck " +
           "            WHERE incomingCheck.company = :company " +
           "            AND incomingCheck.direction = 'INCOMING' " +
           "            AND (incomingCheck.sender = log.sender OR incomingCheck.sender = log.recipient) " +
           "            AND incomingCheck.createdAt >= :sinceTimestamp)")
    List<Long> findActiveChatLastMessageIds(
            @Param("company") Company company,
            @Param("sinceTimestamp") LocalDateTime sinceTimestamp);

    // O Spring Data JPA consegue criar esta query a partir do nome do método
    List<WhatsAppMessageLog> findByIdIn(List<Long> ids, Sort sort);

    /**
     * Encontra a última mensagem de CADA conversa única para uma empresa.
     * Esta query é mais simples agora, pois não precisamos mais checar por mensagens
     * não lidas ou por atividade recente no nível do banco.
     */
    @Query(value = "SELECT wml.* FROM whatsapp_message_logs wml " +
                   "INNER JOIN ( " +
                   "    SELECT " +
                   "        CASE " +
                   "            WHEN direction = 'INCOMING' THEN sender " +
                   "            ELSE recipient " +
                   "        END as contact_phone, " +
                   "        MAX(created_at) as max_created_at " +
                   "    FROM whatsapp_message_logs " +
                   "    WHERE company_id = :companyId " +
                   "    GROUP BY contact_phone " +
                   ") latest ON ((wml.sender = latest.contact_phone OR wml.recipient = latest.contact_phone) AND wml.created_at = latest.max_created_at) " +
                   "WHERE wml.company_id = :companyId " +
                   "ORDER BY wml.created_at DESC",
                   nativeQuery = true)
    List<WhatsAppMessageLog> findLastMessageOfEachChatForCompany(@Param("companyId") Long companyId);

    // 1. Conta mensagens enviadas por usuário
    @Query("SELECT new com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO(l.user.id, COUNT(l)) " +
           "FROM WhatsAppMessageLog l " +
           "WHERE l.company = :company " +
           "AND l.createdAt BETWEEN :start AND :end " +
           "AND l.direction = 'OUTGOING' " +
           "AND l.user IS NOT NULL " +
           "GROUP BY l.user.id")
    List<MetricCountDTO> countMessagesByUser(@Param("company") Company company, 
                                             @Param("start") LocalDateTime start, 
                                             @Param("end") LocalDateTime end);

    // 2. Conta contatos únicos atendidos (respondiste pelo menos 1 msg para este número)
    @Query("SELECT new com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO(l.user.id, COUNT(DISTINCT l.recipient)) " +
           "FROM WhatsAppMessageLog l " +
           "WHERE l.company = :company " +
           "AND l.createdAt BETWEEN :start AND :end " +
           "AND l.direction = 'OUTGOING' " +
           "AND l.user IS NOT NULL " +
           "GROUP BY l.user.id")
    List<MetricCountDTO> countDistinctContactsByUser(@Param("company") Company company, 
                                                     @Param("start") LocalDateTime start, 
                                                     @Param("end") LocalDateTime end);
}
