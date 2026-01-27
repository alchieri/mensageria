package com.br.alchieri.consulting.mensageria.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponseDTO {
    private String externalId;
    private String pixCopyPaste; // Se for Pix
    private String qrCodeUrl;    // Se for Pix (imagem)
    private String paymentUrl;   // Se for Link
    private String status;
}
