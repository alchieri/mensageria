package com.br.alchieri.consulting.mensageria.catalog.service.impl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.catalog.dto.webhook.MetaCatalogEvent;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.catalog.service.CatalogWebhookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogWebhookServiceImpl implements CatalogWebhookService {

    private final ProductRepository productRepository;

    @Transactional
    @Override
    public void processCatalogEvent(MetaCatalogEvent event) {
        if (event.getEntry() == null) return;

        for (MetaCatalogEvent.Entry entry : event.getEntry()) {
            if (entry.getChanges() == null) continue;

            for (MetaCatalogEvent.Change change : entry.getChanges()) {
                updateProductLocal(change.getValue());
            }
        }
    }

    private void updateProductLocal(MetaCatalogEvent.Value value) {
        if (value == null) return;

        // Tenta encontrar o produto pelo ID da Meta ou pelo SKU (Retailer ID)
        // A busca pelo SKU é mais segura para garantir sync com seu ERP
        Optional<Product> productOpt = Optional.empty();

        if (value.getRetailerId() != null) {
            productOpt = productRepository.findBySku(value.getRetailerId()); // Assumindo que você tem findBySku no repo global ou customizado
        }
        
        // Fallback: Busca pelo Facebook ID
        if (productOpt.isEmpty() && value.getId() != null) {
            productOpt = productRepository.findBySku(value.getRetailerId());
        }

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            boolean updated = false;

            // Atualiza Disponibilidade
            if (value.getAvailability() != null) {
                boolean isAvailable = "in stock".equalsIgnoreCase(value.getAvailability());
                if (product.isInStock() != isAvailable) {
                    product.setInStock(isAvailable);
                    updated = true;
                }
            }

            // Atualiza Preço
            if (value.getPrice() != null) {
                BigDecimal newPrice = parsePrice(value.getPrice());
                // Compara BigDecimal de forma segura
                if (product.getPrice() == null || product.getPrice().compareTo(newPrice) != 0) {
                    product.setPrice(newPrice);
                    updated = true;
                }
            }

            if (updated) {
                productRepository.save(product);
                log.info("Produto atualizado via Webhook: {} (SKU: {})", product.getName(), product.getSku());
            }
        } else {
            log.warn("Recebido webhook de produto desconhecido: Meta ID={}, SKU={}", value.getId(), value.getRetailerId());
        }
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null) return BigDecimal.ZERO;
        try {
            // Remove caracteres não numéricos exceto ponto e vírgula
            String clean = priceStr.replaceAll("[^0-9.,]", "");
            clean = clean.replace(",", "."); // Padroniza decimal
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
