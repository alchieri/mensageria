package com.br.alchieri.consulting.mensageria.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.br.alchieri.consulting.mensageria.model.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

}
