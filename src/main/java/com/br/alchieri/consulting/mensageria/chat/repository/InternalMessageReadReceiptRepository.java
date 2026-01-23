package com.br.alchieri.consulting.mensageria.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO;
import com.br.alchieri.consulting.mensageria.chat.model.InternalMessageReadReceipt;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface InternalMessageReadReceiptRepository extends JpaRepository<InternalMessageReadReceipt, Long>{

    // Conta quantas vezes o usuário "leu" chats (pode agrupar por contato único se quiser, aqui conta total de aberturas)
    // Se quiser contatos únicos lidos, mude COUNT(r) para COUNT(DISTINCT r.contact)
    @Query("SELECT new com.br.alchieri.consulting.mensageria.chat.dto.response.MetricCountDTO(r.user.id, COUNT(r)) " +
           "FROM InternalMessageReadReceipt r " +
           "WHERE r.contact.company = :company " +
           "AND r.readAt BETWEEN :start AND :end " +
           "GROUP BY r.user.id")
    List<MetricCountDTO> countReadsByUser(@Param("company") Company company, 
                                          @Param("start") LocalDateTime start, 
                                          @Param("end") LocalDateTime end);
}
