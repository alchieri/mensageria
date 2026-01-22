package com.br.alchieri.consulting.mensageria.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dados para fazer o upload de uma nova chave pública para a Meta.")
public class UploadPublicKeyRequest {

    @NotBlank(message = "A chave pública no formato PEM é obrigatória.")
    @Schema(description = "O conteúdo completo da chave pública, incluindo os delimitadores '-----BEGIN PUBLIC KEY-----' e '-----END PUBLIC KEY-----'.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy...\n...\n...FwIDAQAB\n-----END PUBLIC KEY-----")
    private String publicKey;

    private Long companyId;
}
