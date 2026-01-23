package com.br.alchieri.consulting.mensageria.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.br.alchieri.consulting.mensageria.chat.model.BotStep;

public interface BotStepRepository extends JpaRepository<BotStep, Long> {

}
