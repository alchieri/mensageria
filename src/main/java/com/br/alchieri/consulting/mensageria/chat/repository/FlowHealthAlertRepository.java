package com.br.alchieri.consulting.mensageria.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.FlowHealthAlert;

@Repository
public interface FlowHealthAlertRepository extends JpaRepository<FlowHealthAlert, Long> {

}
