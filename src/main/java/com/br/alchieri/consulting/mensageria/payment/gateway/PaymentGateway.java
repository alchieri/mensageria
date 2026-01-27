package com.br.alchieri.consulting.mensageria.payment.gateway;

import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentProvider;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentStatus;
import com.br.alchieri.consulting.mensageria.payment.dto.PaymentResponseDTO;
import com.br.alchieri.consulting.mensageria.payment.model.PaymentConfig;

public interface PaymentGateway {

    PaymentProvider getProviderName();

    PaymentResponseDTO createPixPayment(Order order, PaymentConfig config);

    PaymentResponseDTO createPaymentLink(Order order, PaymentConfig config);

    PaymentStatus checkPaymentStatus(String externalId, PaymentConfig config);
}
