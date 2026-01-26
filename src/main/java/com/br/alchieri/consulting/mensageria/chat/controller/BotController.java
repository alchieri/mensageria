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

import com.br.alchieri.consulting.mensageria.chat.dto.request.BotStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotWithStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotResponseDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotStepDTO;
import com.br.alchieri.consulting.mensageria.chat.service.BotManagementService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bots")
@Tag(name = "Bot Builder", description = "API para gestão e construção de fluxos de chat automatizados (Bots).")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BotController {

    private final BotManagementService botService;
    private final SecurityUtils securityUtils;

    // --- BOTS ---

    @GetMapping
    @Operation(summary = "Listar Bots", description = "Retorna uma lista de todos os bots configurados para a empresa do usuário logado.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista recuperada com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content)
    })
    public ResponseEntity<ApiResponse> listBots() {
        User user = securityUtils.getAuthenticatedUser();
        List<BotResponseDTO> bots = botService.listBots(user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Bots listados", bots));
    }

    @PostMapping
    @Operation(summary = "Criar Bot Completo", 
               description = "Cria um novo bot definindo suas configurações e, opcionalmente, toda a sua estrutura de passos de uma vez.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bot criado com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<ApiResponse> createBot(@Valid @RequestBody CreateBotWithStructureRequest request) {
        User user = securityUtils.getAuthenticatedUser();
        
        // Chama o novo serviço completo
        BotResponseDTO bot = botService.createBotWithStructure(user.getCompany(), request);
        
        return ResponseEntity.ok(new ApiResponse(true, "Bot criado com sucesso", bot));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir Bot", description = "Remove permanentemente um bot e todos os seus passos e opções associados.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bot removido com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bot não encontrado", content = @Content)
    })
    public ResponseEntity<ApiResponse> deleteBot(@PathVariable Long id) {
        User user = securityUtils.getAuthenticatedUser();
        botService.deleteBot(id, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Bot removido", null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter Bot", description = "Retorna os detalhes de um bot específico.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bot removido com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bot não encontrado", content = @Content)
    })
    public ResponseEntity<ApiResponse> getBot(@PathVariable Long id) {
        User user = securityUtils.getAuthenticatedUser();
        BotResponseDTO bot = botService.getBot(id, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Bot recuperado", bot));
    }

    // --- STEPS (PASSOS) ---

    @GetMapping("/steps/{stepId}")
    @Operation(summary = "Obter Detalhes do Passo", description = "Retorna o conteúdo completo de um passo específico, incluindo suas opções de resposta.")
    public ResponseEntity<ApiResponse> getStep(@PathVariable Long stepId) {
        // TODO: Adicionar validação de segurança se o step pertence à company do user
        BotStepDTO step = botService.getStepDetails(stepId);
        return ResponseEntity.ok(new ApiResponse(true, "Passo recuperado", step));
    }

    @PostMapping("/{botId}/steps")
    @Operation(summary = "Adicionar Passo ao Bot", description = "Cria um novo passo isolado para o bot. Use o endpoint de 'linkSteps' para conectá-lo.")
    public ResponseEntity<ApiResponse> addStep(@PathVariable Long botId, @RequestBody BotStepDTO stepDto) {
        User user = securityUtils.getAuthenticatedUser();
        BotStepDTO created = botService.addStep(botId, stepDto, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Passo criado", created));
    }
    
    @PutMapping("/steps/{stepId}")
    @Operation(summary = "Atualizar Passo", description = "Atualiza o conteúdo, tipo ou metadados de um passo existente.")
    public ResponseEntity<ApiResponse> updateStep(@PathVariable Long stepId, @RequestBody BotStepDTO stepDto) {
        BotStepDTO updated = botService.updateStep(stepId, stepDto);
        return ResponseEntity.ok(new ApiResponse(true, "Passo atualizado", updated));
    }

    // --- OPTIONS (CONEXÕES) ---

    @PostMapping("/steps/{originStepId}/options")
    @Operation(summary = "Conectar Passos (Criar Opção)", description = "Cria uma ligação entre o passo de origem e o passo de destino através de uma opção (botão ou palavra-chave).")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conexão criada com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Passo de origem ou destino não encontrado")
    })
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

    @PutMapping("/{id}/structure")
    @Operation(summary = "Salvar Estrutura (Fluxo) do Bot", 
               description = "Substitui/Sincroniza toda a árvore de passos e opções do bot. Ideal para editores visuais de fluxo.")
    public ResponseEntity<ApiResponse> saveBotStructure(
            @Parameter(description = "ID do Bot") @PathVariable Long id,
            @Valid @RequestBody BotStructureRequest request) {
        
        User user = securityUtils.getAuthenticatedUser();
        botService.saveBotStructure(id, request, user.getCompany());
        
        return ResponseEntity.ok(new ApiResponse(true, "Estrutura do bot salva com sucesso.", null));
    }
}
