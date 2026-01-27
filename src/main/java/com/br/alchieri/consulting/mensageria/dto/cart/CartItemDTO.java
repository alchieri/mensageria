package com.br.alchieri.consulting.mensageria.dto.cart;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO implements Serializable {
    
    private String productRetailerId; // SKU
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    private String currency;
    
    public BigDecimal getTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
