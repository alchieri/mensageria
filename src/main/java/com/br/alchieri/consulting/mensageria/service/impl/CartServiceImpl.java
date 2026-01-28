package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.dto.cart.CartDTO;
import com.br.alchieri.consulting.mensageria.dto.cart.CartItemDTO;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Address;
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
            sessionService.saveSession(session); 
        }
    }
    
    @Override
    public void clearCart(UserSession session) {

        session.getCart().clear();
        sessionService.saveSession(session);
    }

    @Transactional
    @Override
    public Order checkout(UserSession session, Contact contact, WhatsAppPhoneNumber channel, Address deliveryAddress) {
        
        CartDTO cart = session.getCart();
        
        if (cart.isEmpty()) return null;

        for (CartItemDTO cartItem : cart.getItems()) {
            
            Product product = productRepository.findByCompanyIdAndSku(session.getCompanyId(), cartItem.getProductRetailerId())
                    .orElseThrow(() -> new BusinessException("Produto indisponível ou removido: " + cartItem.getName()));

            if (!product.isInStock() || (product.getStockQuantity() != null && product.getStockQuantity() < cartItem.getQuantity())) {
                throw new BusinessException("Estoque insuficiente para o produto: " + product.getName());
            }
        }

        Order order = new Order();
        order.setCompany(contact.getCompany());
        order.setContact(contact);
        order.setChannel(channel);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(cart.getTotalAmount());
        if (!cart.getItems().isEmpty()) {
            order.setCurrency(cart.getItems().get(0).getCurrency());
        }

        if (deliveryAddress != null) {
            order.setDeliveryAddress(deliveryAddress);
        }

        for (CartItemDTO cartItem : cart.getItems()) {
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            
            Product product = productRepository.findByCompanyIdAndSkuWithLock(session.getCompanyId(), cartItem.getProductRetailerId())
                    .orElseThrow(() -> new BusinessException("Produto não encontrado durante o processamento: " + cartItem.getName()));

            if (!product.isInStock() || (product.getStockQuantity() != null && product.getStockQuantity() < cartItem.getQuantity())) {
                throw new BusinessException("Desculpe, o produto '" + product.getName() + "' acabou de esgotar.");
            }

            if (product.getStockQuantity() != null) {
                int newQuantity = product.getStockQuantity() - cartItem.getQuantity();
                product.setStockQuantity(newQuantity);
                
                // Se zerou, atualiza flag
                if (newQuantity <= 0) {
                    product.setInStock(false);
                }
                productRepository.save(product);
            }

            orderItem.setProduct(product);
            orderItem.setProductSku(cartItem.getProductRetailerId());
            orderItem.setProductName(cartItem.getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getUnitPrice());
            orderItem.setTotalPrice(cartItem.getTotal());
            
            order.getItems().add(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        
        clearCart(session);
        
        return savedOrder;
    }
}
