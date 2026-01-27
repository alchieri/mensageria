package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.dto.cart.CartDTO;
import com.br.alchieri.consulting.mensageria.dto.cart.CartItemDTO;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.cart.OrderItem;
import com.br.alchieri.consulting.mensageria.model.enums.OrderStatus;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
import com.br.alchieri.consulting.mensageria.repository.OrderRepository;
import com.br.alchieri.consulting.mensageria.service.CartService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SessionService sessionService; // Precisa expor método save/updateSession

    /**
     * Adiciona item ao carrinho da sessão (Modo Conversacional).
     */
    @Override
    public void addItemToSession(UserSession session, String sku, int quantity) {

        Optional<Product> optProduct = productRepository.findByCompanyIdAndSku(session.getCompanyId(), sku);
        
        if (optProduct.isPresent()) {
            Product p = optProduct.get();
            CartItemDTO item = new CartItemDTO(
                p.getSku(),
                p.getName(),
                quantity,
                p.getPrice(),
                p.getCurrency()
            );
            
            session.getCart().addItem(item);
            
            // Importante: Persistir a sessão atualizada no Redis
            sessionService.saveSession(session); 
        }
    }
    
    /**
     * Esvazia o carrinho.
     */
    @Override
    public void clearCart(UserSession session) {

        session.getCart().clear();
        sessionService.saveSession(session);
    }

    /**
     * Finaliza o pedido: Transforma CartDTO (Redis) em Order (Postgres).
     */
    @Transactional
    @Override
    public Order checkout(UserSession session, Contact contact, WhatsAppPhoneNumber channel) {
        
        CartDTO cart = session.getCart();
        
        if (cart.isEmpty()) return null;

        Order order = new Order();
        order.setCompany(contact.getCompany());
        order.setContact(contact);
        order.setChannel(channel);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(cart.getTotalAmount());
        // Assume moeda do primeiro item ou default BRL
        order.setCurrency(cart.getItems().get(0).getCurrency()); 

        for (CartItemDTO cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            
            // Tenta vincular ao produto real
            productRepository.findByCompanyIdAndSku(session.getCompanyId(), cartItem.getProductRetailerId())
                    .ifPresent(orderItem::setProduct);

            orderItem.setProductSku(cartItem.getProductRetailerId());
            orderItem.setProductName(cartItem.getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getUnitPrice());
            orderItem.setTotalPrice(cartItem.getTotal());
            
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        
        // Limpa o carrinho após sucesso
        clearCart(session);
        
        return savedOrder;
    }
}
