package com.br.alchieri.consulting.mensageria.payment.service;

import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentMethod;

public interface PaymentService {

    Order generatePayment(Long orderId, PaymentMethod method);
    
    void confirmPayment(String externalId, String providerStatus);

    void syncStatusWithProvider(String externalPaymentId);
}
