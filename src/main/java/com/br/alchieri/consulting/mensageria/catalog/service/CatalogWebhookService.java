package com.br.alchieri.consulting.mensageria.catalog.service;

import com.br.alchieri.consulting.mensageria.catalog.dto.webhook.MetaCatalogEvent;

public interface CatalogWebhookService {

    void processCatalogEvent(MetaCatalogEvent event);
}
