package com.br.alchieri.consulting.mensageria.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.br.alchieri.consulting.mensageria.service.BillingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling // Habilita o agendamento de tarefas
@RequiredArgsConstructor
@Slf4j
public class SchedulerConfig {

    private final BillingService billingService;

    /**
     * Roda no primeiro dia de cada mês, à 1 da manhã, para gerar as faturas do mês anterior.
     */
    @Scheduled(cron = "0 0 1 1 * ?") // segundo, minuto, hora, dia-do-mês, mês, dia-da-semana
    public void generateMonthlyInvoicesJob() {
        log.info("JOB DE FATURAMENTO MENSAL: Iniciado.");
        try {
            // Este método no serviço conterá a lógica principal de faturamento
            billingService.generateMonthlyInvoices();
            log.info("JOB DE FATURAMENTO MENSAL: Concluído com sucesso.");
        } catch (Exception e) {
            log.error("JOB DE FATURAMENTO MENSAL: Falha crítica durante a execução.", e);
            // TODO: Enviar notificação de falha para o admin do BSP
        }
    }

    /**
     * Roda todo dia à meia-noite para resetar os contadores DIÁRIOS.
     * O reset mensal é feito pelo job de faturamento.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyCountersJob() {
        log.info("JOB DE RESET DIÁRIO: Iniciado.");
        try {
            billingService.resetDailyUsageCounters(); // Novo método mais específico
            log.info("JOB DE RESET DIÁRIO: Concluído com sucesso.");
        } catch (Exception e) {
            log.error("JOB DE RESET DIÁRIO: Falha durante a execução.", e);
        }
    }
}
