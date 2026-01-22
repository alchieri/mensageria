package com.br.alchieri.consulting.mensageria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class MensageriaApplicationTests {

	@DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Se usar Testcontainers:
        // registry.add("spring.datasource.url", postgres::getJdbcUrl);
        // registry.add("spring.datasource.username", postgres::getUsername);
        // registry.add("spring.datasource.password", postgres::getPassword);

        // Ou, se quiser usar seu RDS para testes de integração (CUIDADO COM DADOS DE TESTE):
         // ... e assim por diante para todas as propriedades que usam placeholders
    }

	@Test
	void contextLoads() {
	}

}
