package com.br.alchieri.consulting.mensageria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.CreatePhoneNumberRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.repository.WhatsAppPhoneNumberRepository;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/channels/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Channels", description = "Gerenciamento dos números de telefone conectados (Canais).")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class WhatsAppPhoneNumberController {

    private final WhatsAppPhoneNumberRepository phoneRepository;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse> listNumbers() {
        User user = securityUtils.getAuthenticatedUser();
        List<WhatsAppPhoneNumber> numbers = phoneRepository.findByCompany(user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Números listados", numbers));
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Adicionar Número", description = "Registra um novo número de telefone (Channel) na empresa.")
    public ResponseEntity<ApiResponse> addNumber(@Valid @RequestBody CreatePhoneNumberRequest request) {
        User user = securityUtils.getAuthenticatedUser();
        Company company = user.getCompany();

        // Verificar se já existe (Globalmente, para evitar conflitos de webhook, ou por empresa)
        // O ideal é que o ID seja único no sistema todo
        if (phoneRepository.findByPhoneNumberId(request.getPhoneNumberId()).isPresent()) {
            throw new BusinessException("Este ID de telefone já está cadastrado no sistema.");
        }

        WhatsAppPhoneNumber phoneNumber = WhatsAppPhoneNumber.builder()
                .company(company)
                .phoneNumberId(request.getPhoneNumberId())
                .wabaId(request.getWabaId())
                .displayPhoneNumber(request.getDisplayPhoneNumber())
                .alias(request.getAlias())
                .status("CONNECTED") // Assume conectado ao criar
                .qualityRating("UNKNOWN") // Atualizado via webhook depois
                .isDefault(false) // Será tratado abaixo
                .build();

        // Lógica de Default:
        // 1. Se o request pede default = true, desmarca os outros.
        // 2. Se for o primeiro número da empresa, força default = true.
        boolean isFirst = phoneRepository.findByCompany(company).isEmpty();

        if (request.isDefault() || isFirst) {
            unsetCurrentDefault(company);
            phoneNumber.setDefault(true);
        }

        WhatsAppPhoneNumber saved = phoneRepository.save(phoneNumber);
        log.info("Novo número adicionado: {} para a empresa {}", saved.getPhoneNumberId(), company.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse(true, "Número adicionado com sucesso.", saved));
    }
    
    @PatchMapping("/{id}/default")
    @Transactional
    @Operation(summary = "Definir como Padrão", description = "Define este número como o padrão para envios da empresa e remove o status dos outros.")
    public ResponseEntity<ApiResponse> setAsDefault(@PathVariable Long id) {
        User user = securityUtils.getAuthenticatedUser();
        Company company = user.getCompany();

        WhatsAppPhoneNumber targetNumber = phoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Número não encontrado."));

        if (!targetNumber.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("Acesso negado a este recurso.");
        }

        if (targetNumber.isDefault()) {
            return ResponseEntity.ok(new ApiResponse(true, "Este número já é o padrão.", targetNumber));
        }

        // Desmarca o anterior
        unsetCurrentDefault(company);

        // Marca o novo
        targetNumber.setDefault(true);
        phoneRepository.save(targetNumber);

        log.info("Número padrão alterado para {} na empresa {}", targetNumber.getPhoneNumberId(), company.getName());

        return ResponseEntity.ok(new ApiResponse(true, "Número definido como padrão com sucesso.", targetNumber));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Remover Número", description = "Remove um número do cadastro. Se for o padrão, você deve definir outro manualmente.")
    public ResponseEntity<ApiResponse> deleteNumber(@PathVariable Long id) {
        User user = securityUtils.getAuthenticatedUser();
        Company company = user.getCompany();

        WhatsAppPhoneNumber targetNumber = phoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Número não encontrado."));

        if (!targetNumber.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("Acesso negado a este recurso.");
        }

        boolean wasDefault = targetNumber.isDefault();
        phoneRepository.delete(targetNumber);
        
        // Se deletou o padrão, verifica se restou algum para promover a padrão (opcional, mas recomendado para evitar erros)
        if (wasDefault) {
            phoneRepository.findByCompany(company).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                phoneRepository.save(next);
                log.info("Número padrão redefinido automaticamente para {} após exclusão.", next.getPhoneNumberId());
            });
        }

        return ResponseEntity.ok(new ApiResponse(true, "Número removido com sucesso.", null));
    }

    /**
     * Método auxiliar para remover a flag 'isDefault' de todos os números da empresa.
     */
    private void unsetCurrentDefault(Company company) {
        phoneRepository.findFirstByCompanyAndIsDefaultTrue(company).ifPresent(current -> {
            current.setDefault(false);
            phoneRepository.save(current);
        });
    }
}
