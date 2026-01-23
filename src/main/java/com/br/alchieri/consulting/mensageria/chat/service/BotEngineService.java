package com.br.alchieri.consulting.mensageria.chat.service;

import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;

public interface BotEngineService {

    /**
     * Tenta iniciar um bot para esta mensagem.
     * Retorna TRUE se um bot assumiu, FALSE se deve seguir para atendimento humano/padrão.
     */
    boolean tryTriggerBot(Company company, Contact contact, UserSession session, User systemUser);

    /**
     * Processa a resposta do usuário para um bot que JÁ está rodando.
     */
    void processInput(String input, Contact contact, UserSession session, User systemUser);
}
