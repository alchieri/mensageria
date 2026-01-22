package com.br.alchieri.consulting.mensageria.controller;

import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.dto.request.RateCardRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.dto.response.MetaRateCardResponse;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;
import com.br.alchieri.consulting.mensageria.service.PlatformConfigService;
import com.br.alchieri.consulting.mensageria.service.RateCardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/rate-cards")
@Tag(name = "Rate Card Management (Admin)", description = "Endpoints para administradores BSP gerenciarem as tabelas de tarifas da Meta.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('BSP_ADMIN')")
@RequiredArgsConstructor
public class RateCardAdminController {

    private final RateCardService rateCardService;
    private final PlatformConfigService platformConfigService; // Para o upload

    @PostMapping
    @Operation(summary = "Criar Nova Tarifa")
    public ResponseEntity<MetaRateCardResponse> createRate(@Valid @RequestBody RateCardRequest request) {
        MetaRateCard createdRate = rateCardService.createRate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MetaRateCardResponse.fromEntity(createdRate));
    }

    @GetMapping
    @Operation(summary = "Listar Todas as Tarifas")
    public ResponseEntity<Page<MetaRateCardResponse>> listRates(@ParameterObject Pageable pageable) {
        Page<MetaRateCard> ratePage = rateCardService.listAllRates(pageable);
        return ResponseEntity.ok(ratePage.map(MetaRateCardResponse::fromEntity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter Tarifa por ID")
    public ResponseEntity<MetaRateCardResponse> getRateById(@PathVariable Long id) {
        return rateCardService.findRateById(id)
                .map(rate -> ResponseEntity.ok(MetaRateCardResponse.fromEntity(rate)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar Tarifa Existente")
    public ResponseEntity<MetaRateCardResponse> updateRate(@PathVariable Long id, @Valid @RequestBody RateCardRequest request) {
        MetaRateCard updatedRate = rateCardService.updateRate(id, request);
        return ResponseEntity.ok(MetaRateCardResponse.fromEntity(updatedRate));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir Tarifa")
    public ResponseEntity<Void> deleteRate(@PathVariable Long id) {
        rateCardService.deleteRate(id);
        return ResponseEntity.noContent().build();
    }

    // --- Endpoints de Upload em Massa ---
    
    @PostMapping(value = "/upload/rates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de Tabela de Tarifas Base (Admin)",
               description = "Faz o upload de um arquivo CSV com as tarifas base (Marketing e 1º tier) da Meta.")
    public ResponseEntity<ApiResponse> uploadBaseRates(
            @Parameter(description = "Arquivo CSV de tarifas base.", required = true) @RequestPart("file") MultipartFile file,
            @Parameter(description = "Data de vigência (YYYY-MM-DD).", required = true) @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {
        return ResponseEntity.ok(platformConfigService.uploadMetaRateCard(file, effectiveDate));
    }

    @PostMapping(value = "/upload/volume-tiers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de Tabela de Níveis de Volume (Admin)",
               description = "Faz o upload de um arquivo CSV com os tiers de volume (Utility, Authentication) da Meta.")
    public ResponseEntity<ApiResponse> uploadVolumeTiers(
            @Parameter(description = "Arquivo CSV de tiers de volume.", required = true) @RequestPart("file") MultipartFile file,
            @Parameter(description = "Data de vigência (YYYY-MM-DD).", required = true) @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {
        return ResponseEntity.ok(platformConfigService.uploadMetaVolumeTiers(file, effectiveDate));
    }
}
