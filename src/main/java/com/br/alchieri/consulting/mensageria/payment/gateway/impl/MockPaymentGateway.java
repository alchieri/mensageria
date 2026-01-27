package com.br.alchieri.consulting.mensageria.payment.gateway.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentProvider;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentStatus;
import com.br.alchieri.consulting.mensageria.payment.dto.PaymentResponseDTO;
import com.br.alchieri.consulting.mensageria.payment.gateway.PaymentGateway;
import com.br.alchieri.consulting.mensageria.payment.model.PaymentConfig;

@Service
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider getProviderName() {
        return PaymentProvider.MOCK;
    }

    @Override
    public PaymentResponseDTO createPixPayment(Order order, PaymentConfig config) {
        // EM PRODUÇÃO: Chamaria API do Asaas/MercadoPago aqui
        return PaymentResponseDTO.builder()
                .externalId(UUID.randomUUID().toString())
                .pixCopyPaste("00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-4266141740005204000053039865405" + order.getTotalAmount() + "5802BR5913Loja Exemplo6008Brasilia62070503***6304ABCD")
                .status("PENDING")
                .build();
    }

    @Override
    public PaymentResponseDTO createPaymentLink(Order order, PaymentConfig config) {
        return PaymentResponseDTO.builder()
                .externalId(UUID.randomUUID().toString())
                .paymentUrl("https://pay.gateway.com/checkout/" + UUID.randomUUID())
                .status("PENDING")
                .build();
    }

    @Override
    public PaymentStatus checkPaymentStatus(String externalId, PaymentConfig config) {
        // TODO Auto-generated method stub
        return null;
    }
}