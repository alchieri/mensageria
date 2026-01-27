package com.br.alchieri.consulting.mensageria.payment.factory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentProvider;
import com.br.alchieri.consulting.mensageria.payment.gateway.PaymentGateway;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentProvider, PaymentGateway> strategies = new EnumMap<>(PaymentProvider.class);

    // Injeção automática de todas as classes que implementam PaymentGateway
    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        for (PaymentGateway gateway : gatewayList) {
            strategies.put(gateway.getProviderName(), gateway);
        }
    }

    public PaymentGateway getGateway(PaymentProvider provider) {
        PaymentGateway gateway = strategies.get(provider);
        if (gateway == null) {
            throw new BusinessException("Provedor de pagamento não implementado ou indisponível: " + provider);
        }
        return gateway;
    }
}
