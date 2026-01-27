package com.br.alchieri.consulting.mensageria.payment.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentMethod;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentStatus;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.payment.dto.PaymentResponseDTO;
import com.br.alchieri.consulting.mensageria.payment.factory.PaymentGatewayFactory;
import com.br.alchieri.consulting.mensageria.payment.gateway.PaymentGateway;
import com.br.alchieri.consulting.mensageria.payment.model.PaymentConfig;
import com.br.alchieri.consulting.mensageria.payment.repository.PaymentConfigRepository;
import com.br.alchieri.consulting.mensageria.payment.service.PaymentService;
import com.br.alchieri.consulting.mensageria.repository.OrderRepository;
import com.br.alchieri.consulting.mensageria.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentConfigRepository paymentConfigRepository; // Repo da Config
    private final UserRepository userRepository;

    private final WhatsAppCloudApiService whatsAppService;

    private final PaymentGatewayFactory gatewayFactory; // A Fábrica

    @Transactional
    @Override
    public Order generatePayment(Long orderId, PaymentMethod method) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado"));

        // 1. Busca configuração da empresa
        PaymentConfig config = paymentConfigRepository.findByCompany(order.getCompany())
                .orElseThrow(() -> new BusinessException("Empresa não possui configuração de pagamento ativa."));
        
        if (!config.isActive()) {
            throw new BusinessException("Gateway de pagamento desativado para esta empresa.");
        }

        // 2. Obtém a estratégia correta (MP, Asaas, etc)
        PaymentGateway gateway = gatewayFactory.getGateway(config.getProvider());

        // 3. Executa
        PaymentResponseDTO response;
        if (method == PaymentMethod.PIX) {
            response = gateway.createPixPayment(order, config); // Passa config com o token
            order.setPixCopyPaste(response.getPixCopyPaste());
        } else {
            response = gateway.createPaymentLink(order, config);
            order.setPaymentUrl(response.getPaymentUrl());
        }

        order.setPaymentMethod(method);
        order.setExternalPaymentId(response.getExternalId());
        order.setPaymentStatus(PaymentStatus.PENDING);
        
        return orderRepository.save(order);
    }

    @Transactional
    @Override
    public void confirmPayment(String externalId, String providerStatus) {
        Order order = orderRepository.findByExternalPaymentId(externalId)
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado: " + externalId));
        
        // Mapeamento simples de status do MP para o nosso sistema
        if ("approved".equalsIgnoreCase(providerStatus) || "CONFIRMED".equalsIgnoreCase(providerStatus)) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if ("rejected".equalsIgnoreCase(providerStatus) || "cancelled".equalsIgnoreCase(providerStatus)) {
            order.setPaymentStatus(PaymentStatus.FAILED);
        }
        
        orderRepository.save(order);
    }

    @Transactional
    @Override
    public void syncStatusWithProvider(String externalPaymentId) {
        log.info("Sincronizando status para pagamento externo ID: {}", externalPaymentId);

        // 1. Busca o Pedido pelo ID externo
        Order order = orderRepository.findByExternalPaymentId(externalPaymentId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado para o pagamento ID: " + externalPaymentId));

        // Se já está pago ou cancelado, ignora (evita processamento duplicado)
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.FAILED) {
            return;
        }

        // 2. Busca a Configuração de Pagamento da Empresa desse Pedido
        PaymentConfig config = paymentConfigRepository.findByCompany(order.getCompany())
                .orElseThrow(() -> new BusinessException("Configuração de pagamento não encontrada para empresa " + order.getCompany().getId()));

        // 3. Obtém o Gateway correto (MP, Asaas, etc)
        PaymentGateway gateway = gatewayFactory.getGateway(config.getProvider());

        // 4. Chama a API do Gateway para ver a verdade
        PaymentStatus realStatus = gateway.checkPaymentStatus(externalPaymentId, config);

        // 5. Se mudou, atualiza e notifica
        if (realStatus != order.getPaymentStatus()) {
            log.info("Atualizando status do pedido #{} de {} para {}", order.getId(), order.getPaymentStatus(), realStatus);
            
            order.setPaymentStatus(realStatus);
            orderRepository.save(order);

            if (realStatus == PaymentStatus.PAID) {
                notifyCustomerPaymentSuccess(order);
            } else if (realStatus == PaymentStatus.FAILED) {
                notifyCustomerPaymentFailed(order);
            }
        }
    }

    private void notifyCustomerPaymentSuccess(Order order) {
        try {
            String msg = "🎉 *Pagamento Confirmado!*\n\n" +
                         "Seu pedido #" + order.getId() + " foi aprovado e já estamos processando.\n" +
                         "Obrigado por comprar conosco!";
            
            // Busca usuário de sistema para enviar a mensagem
            User systemUser = getSystemUser(order.getCompany());
            
            SendTextMessageRequest req = new SendTextMessageRequest();
            req.setTo(order.getContact().getPhoneNumber());
            req.setMessage(msg);
            
            // Define o canal correto (se o pedido tiver essa info, senão usa padrão)
            if (order.getChannel() != null) {
                req.setFromPhoneNumberId(order.getChannel().getPhoneNumberId());
            }

            whatsAppService.sendTextMessage(req, systemUser).subscribe();
            
        } catch (Exception e) {
            log.error("Falha ao notificar cliente sobre pagamento aprovado: {}", e.getMessage());
        }
    }

    private void notifyCustomerPaymentFailed(Order order) {
        try {
            String msg = "⚠️ *Pagamento Rejeitado/Cancelado*\n\n" +
                         "O pagamento do pedido #" + order.getId() + " não foi aprovado.\n" +
                         "Se precisar de ajuda, digite 'Suporte'.";
            
            User systemUser = getSystemUser(order.getCompany());
            
            SendTextMessageRequest req = new SendTextMessageRequest();
            req.setTo(order.getContact().getPhoneNumber());
            req.setMessage(msg);
            if (order.getChannel() != null) {
                req.setFromPhoneNumberId(order.getChannel().getPhoneNumberId());
            }

            whatsAppService.sendTextMessage(req, systemUser).subscribe();
        } catch (Exception e) {
            log.error("Log notification fail", e);
        }
    }
    
    private User getSystemUser(com.br.alchieri.consulting.mensageria.model.Company company) {
         return userRepository.findFirstByCompanyAndRolesContaining(company, Role.ROLE_COMPANY_ADMIN)
                .or(() -> userRepository.findFirstByCompanyAndRolesContaining(company, Role.ROLE_BSP_ADMIN))
                .orElseThrow(() -> new RuntimeException("Nenhum admin encontrado para envio de notificação."));
    }
}
