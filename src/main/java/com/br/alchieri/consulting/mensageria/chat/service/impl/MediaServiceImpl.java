package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.chat.repository.MediaUploadRepository;
import com.br.alchieri.consulting.mensageria.chat.service.MediaService;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaServiceImpl implements MediaService {

    private final MediaUploadRepository mediaUploadRepository;
    private final CompanyRepository companyRepository;
    private final S3Presigner s3Presigner;

    @Override
    public Page<MediaUpload> listMediaForUser(User user, Optional<Long> companyId, Pageable pageable) {
        if (user.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            if (companyId.isPresent()) {
                Company company = companyRepository.findById(companyId.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId.get() + " não encontrada."));
                return mediaUploadRepository.findByCompanyOrderByCreatedAtDesc(company, pageable);
            } else {
                return mediaUploadRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else {
            Company userCompany = user.getCompany();
            if (userCompany == null) {
                throw new BusinessException("Usuário não está associado a uma empresa.");
            }
            if (companyId.isPresent() && !companyId.get().equals(userCompany.getId())) {
                throw new AccessDeniedException("Você não tem permissão para visualizar mídias de outra empresa.");
            }
            return mediaUploadRepository.findByCompanyOrderByCreatedAtDesc(userCompany, pageable);
        }
    }

    @Override
    public String getPresignedUrlForDownload(Long mediaId, User user) {
        MediaUpload mediaUpload = mediaUploadRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mídia com ID " + mediaId + " não encontrada."));

        // Checagem de permissão
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
            !mediaUpload.getCompany().getId().equals(user.getCompany().getId())) {
            throw new AccessDeniedException("Você não tem permissão para acessar esta mídia.");
        }

        // 1. Criar uma requisição para obter o objeto do S3
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(mediaUpload.getS3BucketName())
                .key(mediaUpload.getS3ObjectKey())
                .build();

        // 2. Criar uma requisição de pré-assinatura, definindo a duração da validade
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15)) // URL será válida por 15 minutos
                .getObjectRequest(getObjectRequest)
                .build();

        // 3. Gerar a URL pré-assinada
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        // 4. Retornar a URL como uma string
        return presignedRequest.url().toString();
    }

    
}
