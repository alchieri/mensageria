package com.br.alchieri.consulting.mensageria.chat.service;

import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface MediaService {
    Page<MediaUpload> listMediaForUser(User user, Optional<Long> companyId, Pageable pageable);

    /**
     * Gera uma URL pré-assinada para download de um arquivo do S3.
     * @param mediaId O ID interno (do seu banco) do MediaUpload.
     * @param user O usuário solicitando.
     * @return Uma URL de download com tempo de expiração.
     */
    String getPresignedUrlForDownload(Long mediaId, User user);
}
