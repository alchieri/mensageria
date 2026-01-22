package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações sobre um arquivo de mídia carregado.")
public class MediaUploadResponse {

    private Long id;
    private String metaMediaId;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
    private Long uploadedByUserId;
    private String uploadedByUsername;

    @Schema(description = "Informações da empresa proprietária desta mídia (visível para admins).")
    private ClientTemplateResponse.CompanySummary company; // Reutiliza o DTO de resumo

    public static MediaUploadResponse fromEntity(MediaUpload entity) {
        if (entity == null) return null;
        return MediaUploadResponse.builder()
                .id(entity.getId())
                .metaMediaId(entity.getMetaMediaId())
                .originalFilename(entity.getOriginalFilename())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .createdAt(entity.getCreatedAt())
                .uploadedByUserId(entity.getUploadedBy().getId())
                .uploadedByUsername(entity.getUploadedBy().getUsername())
                .company(ClientTemplateResponse.CompanySummary.fromEntity(entity.getCompany()))
                .build();
    }
}
