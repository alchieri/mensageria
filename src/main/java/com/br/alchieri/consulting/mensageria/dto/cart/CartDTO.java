package com.br.alchieri.consulting.mensageria.dto.cart;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class CartDTO implements Serializable {
    
    private List<CartItemDTO> items = new ArrayList<>();
    
    public void addItem(CartItemDTO newItem) {
        // Verifica se já existe para somar quantidade
        for (CartItemDTO item : items) {
            if (item.getProductRetailerId().equals(newItem.getProductRetailerId())) {
                item.setQuantity(item.getQuantity() + newItem.getQuantity());
                return;
            }
        }
        items.add(newItem);
    }

    public void removeItem(String sku) {
        items.removeIf(i -> i.getProductRetailerId().equals(sku));
    }

    public void clear() {
        items.clear();
    }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(CartItemDTO::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
