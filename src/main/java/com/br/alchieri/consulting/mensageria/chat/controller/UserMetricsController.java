package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.response.UserSupportMetricDTO;
import com.br.alchieri.consulting.mensageria.chat.service.impl.UserMetricsServiceImpl;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics & Reports", description = "Endpoints para relatórios e métricas de desempenho de atendimento.")
@RequiredArgsConstructor
public class UserMetricsController {

    private final UserMetricsServiceImpl userMetricsService;
    private final SecurityUtils securityUtils;

    @GetMapping("/users")
    @Operation(summary = "Métricas de Atendimento por Usuário", 
               description = "Retorna volume de mensagens, contatos atendidos e visualizações por atendente em um período.")
    public ResponseEntity<ApiResponse> getUserMetrics(
            @Parameter(description = "Data inicial (Default: hoje)") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "Data final (Default: hoje)") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        
        // Default: Dia atual (00:00 até 23:59)
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : LocalDate.now().atStartOfDay();
        LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        List<UserSupportMetricDTO> metrics = userMetricsService.getUserMetrics(currentUser.getCompany(), start, end);
        
        return ResponseEntity.ok(new ApiResponse(true, "Métricas recuperadas com sucesso.", metrics));
    }
}
