package com.br.alchieri.consulting.mensageria.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.BotOption;

@Repository
public interface BotOptionRepository extends JpaRepository<BotOption, Long> {

}
