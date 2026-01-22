package com.br.alchieri.consulting.mensageria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableCaching
@EnableAsync // Mantenha se você usa @Async em algum lugar (ex: CallbackService)
@EnableScheduling
@OpenAPIDefinition( // Define informações globais da API
    info = @Info(
        title = "Mensageria WhatsApp API",
        version = "1.0.0",
        description = "API para envio de mensagens e gerenciamento de templates via WhatsApp Cloud e Business APIs.",
        contact = @Contact(
            name = "Alchieri Consulting", // Seu nome ou da empresa
            url = "https://www.alchiericonsulting.com", // Opcional
            email = "alchiericonsulting@gmail.com"  // Opcional
        )
    )
)
public class MensageriaApplication {

	public static void main(String[] args) {

        Hooks.enableAutomaticContextPropagation();
		SpringApplication.run(MensageriaApplication.class, args);
	}

}
