package com.br.alchieri.consulting.mensageria.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.payment.model.PaymentConfig;

@Repository
public interface PaymentConfigRepository extends JpaRepository<PaymentConfig, Long> {

    Optional<PaymentConfig> findByCompany(Company company);
}
