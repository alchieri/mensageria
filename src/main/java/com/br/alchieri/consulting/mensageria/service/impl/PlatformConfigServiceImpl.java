package com.br.alchieri.consulting.mensageria.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.MetaRateCardRepository;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;
import com.br.alchieri.consulting.mensageria.service.PlatformConfigService;
import com.br.alchieri.consulting.mensageria.util.CountryCodeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final WebClient.Builder webClientBuilder;
    private final MetaRateCardRepository rateCardRepository;
    private final CompanyRepository companyRepository;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    // O token para esta operação deve ser um System User Token do seu BSP App
    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    private WebClient getBspWebClient() {
        if (bspSystemUserAccessToken == null || bspSystemUserAccessToken.isBlank()) {
            throw new BusinessException("Token de System User do BSP não configurado.");
        }
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }

    @Override
    public Mono<String> uploadFlowPublicKey(String publicKeyPem, Long companyId) {
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));

        if (company.getMetaPrimaryPhoneNumberId() == null || company.getMetaPrimaryPhoneNumberId().isBlank()) {
            return Mono.error(new BusinessException("O ID do Phone Number da Meta não está configurado."));
        }

        // O endpoint é POST /{app-id}/whatsapp_business_encryption
        String endpoint = "/" + company.getMetaPrimaryPhoneNumberId() + "/whatsapp_business_encryption";
        log.info("Empresa ID {}: Iniciando registro da chave pública para o Phone Number ID {}", companyId, company.getMetaPrimaryPhoneNumberId());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("business_public_key", publicKeyPem);
        
        BodyInserters.FormInserter<String> requestBody = BodyInserters.fromFormData(formData);

        return getBspWebClient().post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Erro da API Meta ao fazer upload da chave pública: Status={}, Body={}",
                                      clientResponse.statusCode(), errorBody);
                            return Mono.error(new BusinessException("Falha ao registrar chave pública na Meta: " + errorBody));
                        })
                )
                .bodyToMono(JsonNode.class)
                .flatMap(postResponseNode -> {
                    if (!postResponseNode.path("success").asBoolean(false)) {
                        log.error("API da Meta retornou 'success: false' ao registrar a chave. Resposta: {}", postResponseNode);
                        return Mono.error(new BusinessException("API da Meta falhou ao registrar a chave pública."));
                    }
                    log.info("Empresa ID {}: Chave pública enviada com sucesso. Buscando o ID da chave...", companyId);

                    return getBspWebClient().get().uri(endpoint).retrieve()
                        .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Erro da API Meta (GET) ao buscar ID da chave para Phone Number ID {}: Status={}, Body={}",
                                            company.getMetaPrimaryPhoneNumberId(), clientResponse.statusCode(), errorBody);
                                    return Mono.error(new BusinessException("Falha ao buscar o ID da chave pública registrada: " + errorBody));
                                })
                        )
                        .bodyToMono(JsonNode.class);
                })
                .flatMap(getResponseNode -> {
                    JsonNode dataArray = getResponseNode.path("data");
                    if (dataArray.isArray() && !dataArray.isEmpty()) {
                        String publicKeyId = dataArray.get(0).path("status").asText(null);
                        if (publicKeyId != null) {
                            log.info("ID da chave pública encontrado: {}. Salvando na empresa ID {}.", publicKeyId, company.getId());
                            
                            // Salva o ID na entidade Company
                            company.setMetaFlowPublicKeyId(publicKeyId);
                            companyRepository.save(company); // O save acontece dentro do fluxo reativo
                            
                            return Mono.just(publicKeyId); // Retorna o ID
                        }
                    }
                    log.warn("Nenhum ID de chave pública encontrado na resposta GET da Meta: {}", getResponseNode);
                    return Mono.error(new ResourceNotFoundException("Nenhuma chave pública registrada foi encontrada para este número de telefone após o upload."));
                });
    }

    @Override
    public Mono<String> getFlowPublicKeyId(Long companyId) {
        
         Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));
        
        if (company == null) {
            return Mono.error(new BusinessException("A empresa do usuário atual não está configurada."));
        }

        if (company.getMetaPrimaryPhoneNumberId() == null || company.getMetaPrimaryPhoneNumberId().isBlank()) {
            return Mono.error(new BusinessException("O ID do Phone Number da Meta não está configurado."));
        }

        String endpoint = "/" + company.getMetaPrimaryPhoneNumberId() + "/whatsapp_business_encryption";
        log.info("Buscando ID da chave pública para o Phone Number ID {}", company.getMetaPrimaryPhoneNumberId());

        return getBspWebClient().get().uri(endpoint).retrieve()
            .bodyToMono(JsonNode.class)
            .map(responseNode -> {
                JsonNode dataArray = responseNode.path("data");
                if (dataArray.isArray() && !dataArray.isEmpty()) {
                    String keyId = dataArray.get(0).path("id").asText(null);
                    if (keyId != null) {
                        log.info("ID da chave pública encontrado: {}", keyId);
                        return keyId;
                    }
                }
                log.warn("Nenhum ID de chave pública encontrado na resposta da Meta: {}", responseNode);
                throw new ResourceNotFoundException("Nenhuma chave pública registrada encontrada para este número de telefone.");
            });
    }

    @Override
    @Transactional
    public ApiResponse uploadMetaRateCard(MultipartFile file, LocalDate effectiveDate) {
        
        if (file.isEmpty() || !"text/csv".equals(file.getContentType())) {
            throw new BusinessException("Arquivo inválido. Apenas arquivos CSV são permitidos.");
        }
        
        int createdCount = 0;
        int updatedCount = 0;
        List<String> errors = new ArrayList<>();
        int lineNumber = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(7).build(); // Pula as 7 primeiras linhas

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                lineNumber++;
                if (line.length < 5) {
                    errors.add("Linha " + (lineNumber + 7) + ": número de colunas insuficiente.");
                    continue;
                }
                
                try {
                    String marketName = line[0].trim();
                    String currency = line[1].trim().replace("$", ""); // Remove '$'
                    String marketingRateStr = line[2].trim();
                    String utilityRateStr = line[3].trim();
                    String authRateStr = line[4].trim();
                    // String authIntlRateStr = line[5].trim(); // Lidar com isso se necessário

                    // Cria/Atualiza a tarifa para cada categoria
                    int[] results = {0, 0}; // [created, updated]
                    results = processRateLine(marketName, currency, TemplateCategory.MARKETING, marketingRateStr, effectiveDate);
                    createdCount += results[0]; updatedCount += results[1];
                    
                    results = processRateLine(marketName, currency, TemplateCategory.UTILITY, utilityRateStr, effectiveDate);
                    createdCount += results[0]; updatedCount += results[1];

                    results = processRateLine(marketName, currency, TemplateCategory.AUTHENTICATION, authRateStr, effectiveDate);
                    createdCount += results[0]; updatedCount += results[1];

                } catch (Exception e) {
                    errors.add("Linha " + (lineNumber + 7) + ": Erro ao processar - " + e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new BusinessException("Erro ao ler o arquivo CSV de tarifas.", e);
        }
        
        String message = String.format("%d tarifas criadas, %d tarifas atualizadas. %d erros encontrados.",
                                       createdCount, updatedCount, errors.size());
        return new ApiResponse(true, message, Map.of("errors", errors));
    }

    /**
     * Processa uma única linha/categoria do CSV, criando uma nova tarifa ou atualizando uma existente (Upsert).
     * @return um array de int `[created, updated]` para contagem.
     */
    private int[] processRateLine(String marketName, String currency, TemplateCategory category, String rateStr, LocalDate effectiveDate) {
        if ("n/a".equalsIgnoreCase(rateStr) || rateStr.isEmpty()) {
            return new int[]{0, 0}; // Ignora se não houver taxa
        }
        
        BigDecimal rate = new BigDecimal(rateStr);
        Long volumeTierStart = 0L; // Assumindo tier base por enquanto

        // Lógica de Upsert:
        // 1. Tenta encontrar a tarifa existente com base na chave única.
        Optional<MetaRateCard> optExistingRate = rateCardRepository
            .findByMarketNameAndCategoryAndEffectiveDateAndVolumeTierStart(
                marketName, category, effectiveDate, volumeTierStart
            );

        MetaRateCard rateCard;
        int created = 0;
        int updated = 0;

        if (optExistingRate.isPresent()) {
            // 2a. Se encontrou, atualiza a tarifa existente.
            rateCard = optExistingRate.get();
            // Apenas atualiza a 'rate' e 'currency', pois os outros campos são a chave de busca.
            // Verifica se a tarifa realmente mudou para evitar um UPDATE desnecessário.
            if (rateCard.getRate().compareTo(rate) != 0 || !rateCard.getCurrency().equals(currency)) {
                log.debug("Atualizando tarifa para [Mercado: {}, Categoria: {}, Data: {}] de {} para {}",
                         marketName, category, effectiveDate, rateCard.getRate(), rate);
                rateCard.setRate(rate);
                rateCard.setCurrency(currency);
                // Não precisa chamar save() explicitamente se o método chamador for @Transactional,
                // mas para clareza e para garantir, podemos chamar.
                rateCardRepository.save(rateCard);
                updated = 1;
            }
        } else {
            // 2b. Se não encontrou, cria uma nova entidade.
            log.debug("Criando nova tarifa para [Mercado: {}, Categoria: {}, Data: {}]",
                     marketName, category, effectiveDate);
            rateCard = new MetaRateCard();
            rateCard.setMarketName(marketName);
            rateCard.setCountryCode(CountryCodeMapper.getCode(marketName));
            rateCard.setCategory(category);
            rateCard.setEffectiveDate(effectiveDate);
            rateCard.setVolumeTierStart(volumeTierStart);
            rateCard.setCurrency(currency);
            rateCard.setRate(rate);
            
            rateCardRepository.save(rateCard);
            created = 1;
        }

        return new int[]{created, updated};
    }

    @Override
    @Transactional
    public ApiResponse uploadMetaVolumeTiers(MultipartFile file, LocalDate effectiveDate) {
        if (file.isEmpty() || !"text/csv".equals(file.getContentType())) {
            throw new BusinessException("Arquivo inválido. Apenas arquivos CSV são permitidos.");
        }
        
        int createdCount = 0;
        int updatedCount = 0;
        List<String> errors = new ArrayList<>();
        int lineNumber = 0;
        String currentMarket = "";
        String currentCurrency = "";

        try (Reader reader = new InputStreamReader(file.getInputStream());
            CSVReader csvReader = new CSVReader(reader)) { // Usar o CSVReader simples
            
            // Pula as primeiras 5 linhas de cabeçalho manualmente
            for (int i = 0; i < 5; i++) {
                csvReader.readNext();
                lineNumber++;
            }

            // A próxima linha deve ser o cabeçalho das colunas, vamos lê-la mas não usá-la
            String[] columnHeaders = csvReader.readNext();
            lineNumber++;
            if (columnHeaders == null) throw new BusinessException("Arquivo CSV não contém cabeçalho de colunas válido.");


            String[] line;
            while ((line = csvReader.readNext()) != null) {
                lineNumber++;
                // Verifica se a linha não está vazia ou cheia de células vazias
                if (Arrays.stream(line).allMatch(s -> s == null || s.trim().isEmpty())) {
                    continue; // Pula linha vazia
                }

                if (line.length < 17) {
                    errors.add("Linha " + lineNumber + ": número de colunas insuficiente (" + line.length + "). Esperado pelo menos 17.");
                    continue;
                }
                
                try {
                    if (StringUtils.hasText(line[0])) {
                        currentMarket = line[0].trim();
                        currentCurrency = line[1].trim().replace("$", "");
                    }

                    // Processar a faixa de UTILITY (Índices: 2, 3, 5)
                    int[] utilityResults = processVolumeTierLine(
                        currentMarket, currentCurrency, TemplateCategory.UTILITY,
                        line[2], line[3], line[5], effectiveDate
                    );
                    createdCount += utilityResults[0];
                    updatedCount += utilityResults[1];

                    // Processar a faixa de AUTHENTICATION (Índices: 7, 8, 10)
                    int[] authResults = processVolumeTierLine(
                        currentMarket, currentCurrency, TemplateCategory.AUTHENTICATION,
                        line[7], line[8], line[10], effectiveDate
                    );
                    createdCount += authResults[0];
                    updatedCount += authResults[1];
                    
                } catch (Exception e) {
                    errors.add("Linha " + lineNumber + ": Erro ao processar - " + e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            log.error("Erro detalhado ao ler o arquivo CSV de tiers.", e);
            throw new BusinessException("Erro ao ler o arquivo CSV de tiers: " + e.getMessage());
        }
        
        String message = String.format("%d faixas de preço criadas, %d faixas atualizadas. %d erros encontrados.",
                                    createdCount, updatedCount, errors.size());
        return new ApiResponse(true, message, Map.of("errors", errors));
    }

    /**
     * Processa uma única linha de tier do CSV, criando ou atualizando a entrada no banco.
     * @return um array de int `[created, updated]` para contagem.
     */
    private int[] processVolumeTierLine(String marketName, String currency, TemplateCategory category,
                                        String fromStr, String toStr, String rateStr, LocalDate effectiveDate) {
        
        if ("n/a".equalsIgnoreCase(fromStr) || fromStr.isEmpty() || "List rate".equalsIgnoreCase(rateStr)) {
            // Ignora linhas de "List rate" (já tratadas pelo outro CSV) ou linhas vazias
            return new int[]{0, 0};
        }
        
        try {
            long from = Long.parseLong(fromStr.replace(",", ""));
            // Se 'To' for '--', consideramos como infinito (null no banco)
            Long to = toStr.trim().equals("--") ? null : Long.parseLong(toStr.replace(",", ""));
            BigDecimal rate = new BigDecimal(rateStr);

            // Lógica de Upsert
            Optional<MetaRateCard> optExistingRate = rateCardRepository
                .findByMarketNameAndCategoryAndEffectiveDateAndVolumeTierStart(
                    marketName, category, effectiveDate, from
                );

            MetaRateCard rateCard;
            int created = 0, updated = 0;

            if (optExistingRate.isPresent()) {
                // Atualiza
                rateCard = optExistingRate.get();
                if (rateCard.getRate().compareTo(rate) != 0 || !Objects.equals(rateCard.getVolumeTierEnd(), to)) {
                    log.debug("Atualizando tier para [Mercado: {}, Categoria: {}, Faixa: {}]: Nova Tarifa: {}",
                            marketName, category, from, rate);
                    rateCard.setRate(rate);
                    rateCard.setVolumeTierEnd(to);
                    rateCardRepository.save(rateCard);
                    updated = 1;
                }
            } else {
                // Cria
                log.debug("Criando novo tier para [Mercado: {}, Categoria: {}, Faixa: {}]",
                        marketName, category, from);
                rateCard = new MetaRateCard();
                rateCard.setMarketName(marketName);
                rateCard.setCountryCode(CountryCodeMapper.getCode(marketName));
                rateCard.setCurrency(currency);
                rateCard.setCategory(category);
                rateCard.setEffectiveDate(effectiveDate);
                rateCard.setVolumeTierStart(from);
                rateCard.setVolumeTierEnd(to);
                rateCard.setRate(rate);
                
                rateCardRepository.save(rateCard);
                created = 1;
            }
            return new int[]{created, updated};

        } catch (NumberFormatException e) {
            log.error("Erro de formatação de número ao processar tier para {}: From='{}', To='{}', Rate='{}'",
                    marketName, fromStr, toStr, rateStr);
            throw new BusinessException("Formato de número inválido no CSV.");
        }
    }
}
