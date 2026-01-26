package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface MediaUploadRepository extends JpaRepository<MediaUpload, Long> {

    // Busca paginada de mídias por empresa, ordenando pela mais recente
    Page<MediaUpload> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);
    
    // Busca paginada de TODAS as mídias (para admins)
    Page<MediaUpload> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Busca mídia por metaMediaId
    Optional<MediaUpload> findByMetaMediaId(String metaMediaId);
}
