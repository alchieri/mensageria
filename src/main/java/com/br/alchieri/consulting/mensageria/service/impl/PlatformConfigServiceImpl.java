package com.br.alchieri.consulting.mensageria.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.repository.MetaRateCardRepository;
import com.br.alchieri.consulting.mensageria.repository.WhatsAppPhoneNumberRepository;
import com.br.alchieri.consulting.mensageria.service.PlatformConfigService;
import com.br.alchieri.consulting.mensageria.util.CountryCodeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final WebClient.Builder webClientBuilder;
    private final MetaRateCardRepository rateCardRepository;
    private final CompanyRepository companyRepository;
    private final WhatsAppPhoneNumberRepository phoneNumberRepository;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    // O token para esta operação deve ser um System User Token do seu BSP App
    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    @Override
    public void uploadFlowPublicKey(String publicKeyPem, Long companyId) {
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        // 1. Busca todos os números da empresa para descobrir as WABAs
        List<WhatsAppPhoneNumber> phoneNumbers = phoneNumberRepository.findByCompany(company);
        
        if (phoneNumbers.isEmpty()) {
            throw new BusinessException("A empresa não possui números de WhatsApp configurados. Não é possível identificar a WABA para upload da chave.");
        }

        // 2. Extrai WABA IDs únicos (para não enviar 2x para a mesma conta)
        Set<String> uniqueWabaIds = phoneNumbers.stream()
                .map(WhatsAppPhoneNumber::getWabaId)
                .filter(wabaId -> wabaId != null && !wabaId.isBlank())
                .collect(Collectors.toSet());

        if (uniqueWabaIds.isEmpty()) {
            throw new BusinessException("Nenhum WABA ID encontrado nos números cadastrados.");
        }

        // 3. Limpa a chave PEM para o formato que a Meta aceita (apenas o base64 sem headers, geralmente)
        // A Meta geralmente aceita o PEM completo ou apenas o corpo. 
        // Vamos assumir que enviamos o PEM limpo ou formatado conforme a doc.
        // Se a Meta pedir "clean string":
        String cleanPublicKey = cleanPemKey(publicKeyPem);

        // 4. Itera e faz upload para cada WABA
        // Usamos block() aqui se o método for void síncrono, ou transformamos em Flux/Mono se for reativo.
        // Assumindo método síncrono por enquanto:
        
        int successCount = 0;
        for (String wabaId : uniqueWabaIds) {
            try {
                uploadKeyToWaba(wabaId, cleanPublicKey);
                successCount++;
            } catch (Exception e) {
                log.error("Falha ao enviar chave pública para WABA {}: {}", wabaId, e.getMessage());
                // Decide se lança exceção ou continua (Melhor esforço)
            }
        }

        if (successCount == 0) {
            throw new BusinessException("Falha ao configurar criptografia de Flows. Nenhuma WABA foi atualizada com sucesso.");
        }
        
        log.info("Chave pública de Flows atualizada para {} WABAs da empresa {}.", successCount, company.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public String getFlowPublicKeyId(Long companyId) {
        
         Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        // 1. Descobrir a WABA através dos números
        String wabaId = phoneNumberRepository.findByCompany(company).stream()
                .map(WhatsAppPhoneNumber::getWabaId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Nenhuma WABA identificada para esta empresa (sem números cadastrados)."));

        // 2. Consultar a API da Meta
        // Endpoint: GET /{waba_id}/flow_json_encryption_public_key
        String url = graphApiBaseUrl + "/" + wabaId + "/flow_json_encryption_public_key";

        try {
            JsonNode response = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // 3. Extrair o ID da chave
            // A resposta é: { "data": [ { "key_id": "123", ... } ] }
            if (response != null && response.has("data") && response.get("data").isArray() && !response.get("data").isEmpty()) {
                // Retorna o primeiro ID encontrado (geralmente é o ativo)
                return response.get("data").get(0).path("key_id").asText();
            } else {
                log.warn("WABA {} não possui chaves de criptografia de Flow configuradas.", wabaId);
                return null; // Ou lançar exceção se for crítico
            }

        } catch (Exception e) {
            log.error("Erro ao buscar Flow Public Key ID na Meta: {}", e.getMessage());
            throw new BusinessException("Falha ao consultar configuração de criptografia na Meta.");
        }
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

    /**
     * Executa o POST na API da Meta.
     * Endpoint: POST /{waba_id}/flow_json_encryption_public_key
     */
    private void uploadKeyToWaba(String wabaId, String publicKey) {
        String url = graphApiBaseUrl + "/" + wabaId + "/flow_json_encryption_public_key";

        Map<String, String> body = new HashMap<>();
        body.put("business_public_key", publicKey);

        JsonNode response = webClientBuilder.build()
                .post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(); // Bloqueante

        log.info("Sucesso upload chave Flow para WABA {}. Response: {}", wabaId, response);
    }

    /**
     * Remove headers e quebras de linha do PEM se necessário.
     * A Meta geralmente aceita o formato PEM padrão, mas se tiver problemas, use apenas o body.
     */
    private String cleanPemKey(String pem) {
        if (pem == null) return null;
        return pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""); // Remove quebras de linha e espaços
    }
}
