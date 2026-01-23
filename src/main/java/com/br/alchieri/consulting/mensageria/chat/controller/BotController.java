package com.br.alchieri.consulting.mensageria.chat.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotResponseDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotStepDTO;
import com.br.alchieri.consulting.mensageria.chat.service.BotManagementService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bots")
@Tag(name = "Bot Builder", description = "Gestão de Bots Dinâmicos")
@RequiredArgsConstructor
public class BotController {

    private final BotManagementService botService;
    private final SecurityUtils securityUtils;

    // --- BOTS ---

    @GetMapping
    @Operation(summary = "Listar Bots", description = "Lista todos os bots da empresa logada.")
    public ResponseEntity<ApiResponse> listBots() {
        User user = securityUtils.getAuthenticatedUser();
        List<BotResponseDTO> bots = botService.listBots(user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Bots listados", bots));
    }

    @PostMapping
    @Operation(summary = "Criar Bot", description = "Cria um novo bot com um passo inicial padrão.")
    public ResponseEntity<ApiResponse> createBot(@Valid @RequestBody CreateBotRequest request) {
        User user = securityUtils.getAuthenticatedUser();
        BotResponseDTO bot = botService.createBot(user.getCompany(), request);
        return ResponseEntity.ok(new ApiResponse(true, "Bot criado com sucesso", bot));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar Bot", description = "Remove o bot e todos os seus passos.")
    public ResponseEntity<ApiResponse> deleteBot(@PathVariable Long id) {
        User user = securityUtils.getAuthenticatedUser();
        botService.deleteBot(id, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Bot removido", null));
    }

    // --- STEPS (PASSOS) ---

    @GetMapping("/steps/{stepId}")
    @Operation(summary = "Detalhes do Passo", description = "Retorna o conteúdo do passo e suas opções de saída.")
    public ResponseEntity<ApiResponse> getStep(@PathVariable Long stepId) {
        // TODO: Adicionar validação de segurança se o step pertence à company do user
        BotStepDTO step = botService.getStepDetails(stepId);
        return ResponseEntity.ok(new ApiResponse(true, "Passo recuperado", step));
    }

    @PostMapping("/{botId}/steps")
    @Operation(summary = "Adicionar Passo", description = "Cria um novo passo solto (sem conexão ainda) para o bot.")
    public ResponseEntity<ApiResponse> addStep(@PathVariable Long botId, @RequestBody BotStepDTO stepDto) {
        User user = securityUtils.getAuthenticatedUser();
        BotStepDTO created = botService.addStep(botId, stepDto, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Passo criado", created));
    }
    
    @PutMapping("/steps/{stepId}")
    @Operation(summary = "Atualizar Passo", description = "Atualiza texto, tipo ou metadados de um passo.")
    public ResponseEntity<ApiResponse> updateStep(@PathVariable Long stepId, @RequestBody BotStepDTO stepDto) {
        BotStepDTO updated = botService.updateStep(stepId, stepDto);
        return ResponseEntity.ok(new ApiResponse(true, "Passo atualizado", updated));
    }

    // --- OPTIONS (CONEXÕES) ---

    @PostMapping("/steps/{originStepId}/options")
    @Operation(summary = "Conectar Passos", description = "Cria uma opção (botão/palavra-chave) ligando o passo A ao passo B.")
    public ResponseEntity<ApiResponse> linkSteps(
            @PathVariable Long originStepId,
            @RequestBody Map<String, Object> payload) {
        
        // Payload: { "targetStepId": 12, "keyword": "1", "label": "Financeiro" }
        Long targetStepId = ((Number) payload.get("targetStepId")).longValue();
        String keyword = (String) payload.get("keyword");
        String label = (String) payload.get("label");

        botService.linkSteps(originStepId, targetStepId, keyword, label);
        return ResponseEntity.ok(new ApiResponse(true, "Passos conectados", null));
    }
}
