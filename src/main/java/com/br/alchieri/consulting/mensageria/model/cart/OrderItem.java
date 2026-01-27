package com.br.alchieri.consulting.mensageria.model.cart;

import java.math.BigDecimal;

import com.br.alchieri.consulting.mensageria.catalog.model.Product;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_items")
@Data
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // Relacionamento opcional com Product, pois o produto pode ser deletado do catálogo,
    // mas o histórico do pedido deve permanecer.
    @ManyToOne 
    @JoinColumn(name = "product_id")
    private Product product;

    private String productSku; // Snapshot
    private String productName; // Snapshot
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
