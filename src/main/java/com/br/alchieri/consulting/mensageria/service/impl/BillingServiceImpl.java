package com.br.alchieri.consulting.mensageria.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.response.CompanyUsageResponse;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.chat.repository.ClientTemplateRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.dto.request.CreateBillingPlanRequest;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.BillingPlan;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.Invoice;
import com.br.alchieri.consulting.mensageria.model.InvoiceItem;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;
import com.br.alchieri.consulting.mensageria.repository.BillingPlanRepository;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.repository.InvoiceRepository;
import com.br.alchieri.consulting.mensageria.repository.MetaRateCardRepository;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.br.alchieri.consulting.mensageria.util.CountryCodeMapper;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingServiceImpl implements BillingService {

    private final BillingPlanRepository billingPlanRepository;
    private final CompanyRepository companyRepository;
    private final MetaRateCardRepository rateCardRepository;
    private final ClientTemplateRepository clientTemplateRepository;
    private final FlowRepository flowRepository;
    private final InvoiceRepository invoiceRepository;

    private final StringRedisTemplate redisTemplate;

    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @Override
    @Transactional
    public BillingPlan createOrUpdateCompanyBillingPlan(CreateBillingPlanRequest request) {
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + request.getCompanyId() + " não encontrada."));

        BillingPlan plan = billingPlanRepository.findByCompany(company).orElseGet(BillingPlan::new);

        plan.setCompany(company);
        plan.setId(company.getId());
        plan.setBillingType(request.getBillingType());
        plan.setMonthlyFee(request.getMonthlyFee());
        plan.setMonthlyMessageLimit(request.getMonthlyMessageLimit());
        plan.setDailyMessageLimit(request.getDailyMessageLimit());
        plan.setPlatformFeePerMessage(request.getPlatformFeePerMessage());
        plan.setMetaCostMarkupPercentage(request.getMetaCostMarkupPercentage());
        plan.setActiveTemplateLimit(request.getActiveTemplateLimit());
        plan.setPricePerExceededActiveTemplate(request.getPricePerExceededActiveTemplate());
        plan.setActiveFlowLimit(request.getActiveFlowLimit());
        plan.setPricePerExceededActiveFlow(request.getPricePerExceededActiveFlow());
        plan.setMonthlyCampaignLimit(request.getMonthlyCampaignLimit());
        plan.setPricePerExceededCampaign(request.getPricePerExceededCampaign());

        if (plan.getLastMonthlyReset() == null) {
            resetAllCounters(plan, LocalDateTime.now());
        }

        return billingPlanRepository.save(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingPlan> getBillingPlanByCompanyId(Long companyId) {
        return billingPlanRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyUsageResponse> getClientCompanyUsage(Company company) {
        
        if (company == null) return Optional.empty();
        
        return billingPlanRepository.findByCompany(company).map(plan -> {
            // Conta os recursos ativos dinamicamente
            long activeTemplates = clientTemplateRepository.countByCompanyAndStatusIn(company, List.of("APPROVED", "PENDING"));
            long activeFlows = flowRepository.countByCompanyAndStatus(company, FlowStatus.PUBLISHED);

            // Simula o reset para obter os contadores corretos para o momento da consulta
            LocalDateTime now = LocalDateTime.now();
            int currentDayMsg = plan.getCurrentDayMessagesSent();
            int currentMonthMsg = plan.getCurrentMonthMessagesSent();
            int currentMonthCampaigns = plan.getCurrentMonthCampaignsExecuted();
            BigDecimal metaCost = plan.getCurrentMonthMetaCost();
            BigDecimal platformFee = plan.getCurrentMonthPlatformFee();

            if (plan.getLastDailyReset() == null || plan.getLastDailyReset().toLocalDate().isBefore(now.toLocalDate())) {
                currentDayMsg = 0;
            }
            if (plan.getLastMonthlyReset() == null || YearMonth.from(plan.getLastMonthlyReset()).isBefore(YearMonth.from(now))) {
                currentMonthMsg = 0;
                currentMonthCampaigns = 0;
                metaCost = BigDecimal.ZERO;
                platformFee = BigDecimal.ZERO;
            }
            
            CompanyUsageResponse.UsageCounts counts = new CompanyUsageResponse.UsageCounts(
                (int) activeTemplates, (int) activeFlows, currentMonthCampaigns,
                currentDayMsg, currentMonthMsg, metaCost, platformFee
            );
            
            return CompanyUsageResponse.fromBillingPlan(plan, counts);
        });
    }

    @Override
    public Page<BillingPlan> findAllBillingPlans(Pageable pageable) {
        // TODO: Adicionar checagem se o chamador é ROLE_BSP_ADMIN
        return billingPlanRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCompanySendMessages(Company company, int messageCount) {
        BillingPlan plan = billingPlanRepository.findByCompany(company)
            .orElseThrow(() -> new BusinessException("Plano de cobrança não encontrado para a empresa."));
        
        resetCountersIfNeeded(plan);

        if (plan.getDailyMessageLimit() != null && (plan.getCurrentDayMessagesSent() + messageCount) > plan.getDailyMessageLimit()) {
            log.warn("Empresa ID {}: Limite diário de mensagens ({}) excedido.", company.getId(), plan.getDailyMessageLimit());
            return false;
        }
        if ((plan.getCurrentMonthMessagesSent() + messageCount) > plan.getMonthlyMessageLimit()) {
            log.warn("Empresa ID {}: Limite mensal de mensagens ({}) excedido.", company.getId(), plan.getMonthlyMessageLimit());
            return false;
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCompanyCreateTemplate(Company company) {
        BillingPlan plan = billingPlanRepository.findByCompany(company).orElseThrow(() -> new BusinessException("Plano de cobrança não encontrado."));
        long activeTemplates = clientTemplateRepository.countByCompanyAndStatusIn(company, List.of("APPROVED", "PENDING"));
        if (activeTemplates >= plan.getActiveTemplateLimit()) {
            log.warn("Empresa ID {}: Limite de templates ativos ({}) atingido.", company.getId(), plan.getActiveTemplateLimit());
            return false;
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCompanyCreateFlow(Company company) {
        BillingPlan plan = billingPlanRepository.findByCompany(company).orElseThrow(() -> new BusinessException("Plano de cobrança não encontrado."));
        long activeFlows = flowRepository.countByCompanyAndStatus(company, FlowStatus.PUBLISHED);
        if (activeFlows >= plan.getActiveFlowLimit()) {
            log.warn("Empresa ID {}: Limite de flows ativos ({}) atingido.", company.getId(), plan.getActiveFlowLimit());
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public boolean canCompanyExecuteCampaign(Company company) {
        BillingPlan plan = getAndResetPlanIfNeeded(company);
        if (plan == null) return false;
        if (plan.getCurrentMonthCampaignsExecuted() >= plan.getMonthlyCampaignLimit()) {
            log.warn("Empresa ID {}: Limite mensal de campanhas ({}) atingido.", company.getId(), plan.getMonthlyCampaignLimit());
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void recordMessagesSent(Company company, int messageCount) {
        billingPlanRepository.findByCompany(company).ifPresent(plan -> {
            resetCountersIfNeeded(plan);
            plan.setCurrentDayMessagesSent(plan.getCurrentDayMessagesSent() + messageCount);
            plan.setCurrentMonthMessagesSent(plan.getCurrentMonthMessagesSent() + messageCount);
        });
    }

    @Override
    @Transactional
    public void recordCampaignExecution(Company company) {
        billingPlanRepository.findByCompany(company).ifPresent(plan -> {
            resetCountersIfNeeded(plan);
            plan.setCurrentMonthCampaignsExecuted(plan.getCurrentMonthCampaignsExecuted() + 1);
        });
    }

    @Override
    @Transactional
    public void recordCosts(Company company, BigDecimal metaCost, BigDecimal platformFee) {
        if (company == null || metaCost == null || platformFee == null) return;
        billingPlanRepository.findByCompany(company).ifPresent(plan -> {
            resetCountersIfNeeded(plan);
            plan.setCurrentMonthMetaCost(plan.getCurrentMonthMetaCost().add(metaCost));
            plan.setCurrentMonthPlatformFee(plan.getCurrentMonthPlatformFee().add(platformFee));
        });
    }

    @Override
    @Transactional
    public BigDecimal calculateMetaCostForMessage(WhatsAppMessageLog messageLog) {
        
        
        if (messageLog.getPricingCategory() == null || messageLog.getCompany() == null) {
            return BigDecimal.ZERO;
        }

        TemplateCategory category = TemplateCategory.valueOf(messageLog.getPricingCategory().toUpperCase());
        String companyId = messageLog.getCompany().getId().toString();
        String phoneNumber = messageLog.getRecipient(); // O destino (cliente)

        // Chave do Redis para controlar a janela ativa
        // Formato: window:{companyId}:{phoneNumber}:{category} ou genérico se a categoria for hierárquica
        // Simplificação: A Meta permite janelas sobrepostas de categorias diferentes.
        String windowKey = "billing_window:" + companyId + ":" + phoneNumber + ":" + category.name();

        // Verifica se já existe uma janela aberta para esta categoria e destinatário
        Boolean hasActiveWindow = redisTemplate.hasKey(windowKey);

        if (Boolean.TRUE.equals(hasActiveWindow)) {
            log.info("Janela de 24h ativa para [Empresa: {}, Fone: {}, Categ: {}]. Custo Meta = ZERO.", companyId, phoneNumber, category);
            return BigDecimal.ZERO;
        }

        // --- NOVA JANELA DE COBRANÇA ---
        log.info("Abrindo nova janela de conversação de 24h.");
        
        // 1. Calcula o custo da abertura da janela
        BigDecimal cost = getRateForCategory(messageLog, category); // Método auxiliar que busca no RateCardRepository

        // 2. Registra a janela no Redis com TTL de 24 horas
        redisTemplate.opsForValue().set(windowKey, "ACTIVE", Duration.ofHours(24));

        return cost;
    }

    private BigDecimal getRateForCategory(WhatsAppMessageLog messageLog, TemplateCategory category) {
        String recipient = messageLog.getRecipient();
        String countryCode = extractCountryCode(recipient);
        String marketOrRegion = CountryCodeMapper.getMarketOrRegion(countryCode);
        
        return rateCardRepository.findEffectiveRate(marketOrRegion, category, 1L, LocalDate.now())
                .map(MetaRateCard::getRate)
                .orElse(BigDecimal.ZERO);
    }
    
    @Override
    public BigDecimal calculatePlatformFee(WhatsAppMessageLog messageLog) {
         BillingPlan plan = billingPlanRepository.findByCompany(messageLog.getCompany()).orElse(null);
         if (plan == null) return BigDecimal.ZERO;
         
         // Lógica: Custo Meta * Markup % + Taxa Fixa
         BigDecimal metaCost = messageLog.getMetaCost() != null ? messageLog.getMetaCost() : BigDecimal.ZERO;
         BigDecimal markupAmount = metaCost.multiply(plan.getMetaCostMarkupPercentage().divide(new BigDecimal("100")));
         return plan.getPlatformFeePerMessage().add(markupAmount);
    }

    @Override
    @Transactional
    public void resetUsageCounters() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Executando job de reset de contadores de uso em {}", now);
        List<BillingPlan> plans = billingPlanRepository.findAll();
        for (BillingPlan plan : plans) {
            resetCountersIfNeeded(plan);
        }
    }

    @Override
    @Transactional
    public void generateMonthlyInvoices() {
        YearMonth billingPeriod = YearMonth.now().minusMonths(1); // Faturamento do mês anterior
        log.info("Iniciando geração de faturas para o período: {}", billingPeriod);

        List<Company> companiesWithPlans = billingPlanRepository.findAll().stream()
                                                .map(BillingPlan::getCompany)
                                                .toList();
        
        for (Company company : companiesWithPlans) {
            try {
                generateInvoiceForCompany(company, billingPeriod);
            } catch (Exception e) {
                log.error("Falha ao gerar fatura para a empresa ID {}: {}", company.getId(), e.getMessage(), e);
                // Continua para a próxima empresa
            }
        }
    }
    
    /**
     * Lógica principal de geração de fatura para uma única empresa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Cada fatura em sua própria transação
    public void generateInvoiceForCompany(Company company, YearMonth billingPeriod) {
        log.info("Gerando fatura para Empresa ID {} para o período {}", company.getId(), billingPeriod);

        BillingPlan plan = billingPlanRepository.findByCompany(company)
            .orElseThrow(() -> new BusinessException("Empresa não possui plano para faturamento."));

        // Não gera fatura se o plano foi resetado neste mês (evita duplicatas)
        if (plan.getLastMonthlyReset() != null && YearMonth.from(plan.getLastMonthlyReset()).equals(YearMonth.now())) {
            log.warn("Fatura para Empresa ID {} para o período {} já pode ter sido gerada. Pulando.", company.getId(), billingPeriod);
            return;
        }

        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setBillingPeriod(billingPeriod);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(15)); // Vencimento em 15 dias

        // --- CÁLCULO DOS ITENS DA FATURA ---
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        // 1. Mensalidade
        invoice.addItem(new InvoiceItem("Assinatura do Plano Mensal", 1, plan.getMonthlyFee()));
        totalAmount = totalAmount.add(plan.getMonthlyFee());
        
        // 2. Custos de Mensagens
        invoice.addItem(new InvoiceItem("Custo de Mensagens (Repasse Meta)", 1, plan.getCurrentMonthMetaCost()));
        totalAmount = totalAmount.add(plan.getCurrentMonthMetaCost());
        
        invoice.addItem(new InvoiceItem("Taxa de Plataforma sobre Mensagens", 1, plan.getCurrentMonthPlatformFee()));
        totalAmount = totalAmount.add(plan.getCurrentMonthPlatformFee());
        
        // 3. Custo de Templates Ativos Excedentes
        long activeTemplates = clientTemplateRepository.countByCompanyAndStatusIn(company, List.of("APPROVED"));
        long exceededTemplates = Math.max(0, activeTemplates - plan.getActiveTemplateLimit());
        if (exceededTemplates > 0) {
            BigDecimal exceededTemplateCost = plan.getPricePerExceededActiveTemplate().multiply(new BigDecimal(exceededTemplates));
            invoice.addItem(new InvoiceItem("Templates Ativos Excedentes", (int) exceededTemplates, plan.getPricePerExceededActiveTemplate()));
            totalAmount = totalAmount.add(exceededTemplateCost);
        }
        
        // 4. Custo de Flows Ativos Excedentes
        long activeFlows = flowRepository.countByCompanyAndStatus(company, FlowStatus.PUBLISHED);
        long exceededFlows = Math.max(0, activeFlows - plan.getActiveFlowLimit());
        if (exceededFlows > 0) {
            BigDecimal exceededFlowCost = plan.getPricePerExceededActiveFlow().multiply(new BigDecimal(exceededFlows));
            invoice.addItem(new InvoiceItem("Flows Ativos Excedentes", (int) exceededFlows, plan.getPricePerExceededActiveFlow()));
            totalAmount = totalAmount.add(exceededFlowCost);
        }
        
        // 5. Custo de Campanhas Excedentes
        long exceededCampaigns = Math.max(0, plan.getCurrentMonthCampaignsExecuted() - plan.getMonthlyCampaignLimit());
        if (exceededCampaigns > 0) {
            BigDecimal exceededCampaignCost = plan.getPricePerExceededCampaign().multiply(new BigDecimal(exceededCampaigns));
            invoice.addItem(new InvoiceItem("Campanhas Excedentes", (int) exceededCampaigns, plan.getPricePerExceededCampaign()));
            totalAmount = totalAmount.add(exceededCampaignCost);
        }
        
        invoice.setTotalAmount(totalAmount);
        invoiceRepository.save(invoice);
        log.info("Fatura ID {} gerada para Empresa ID {} com valor total de {}", invoice.getId(), company.getId(), totalAmount);
        
        // Após gerar a fatura, reseta os contadores
        resetAllCounters(plan, LocalDateTime.now());
        billingPlanRepository.save(plan);
    }

    @Override
    @Transactional
    public void resetDailyUsageCounters() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Executando reset de contadores DIÁRIOS em {}", now);
        List<BillingPlan> plans = billingPlanRepository.findAll();
        for (BillingPlan plan : plans) {
            if (plan.getLastDailyReset() == null || plan.getLastDailyReset().toLocalDate().isBefore(now.toLocalDate())) {
                plan.setCurrentDayMessagesSent(0);
                plan.setLastDailyReset(now.withHour(0).withMinute(0).withSecond(0).withNano(0));
            }
        }
        // O @Transactional cuidará do save
    }

    // --- Métodos Helper ---

    /**
     * Extrai o código do país de um número de telefone usando a biblioteca libphonenumber.
     * @param phoneNumber O número de telefone (ex: 5511999998888).
     * @return A string do código do país (ex: "55") ou "UNKNOWN" se não for possível fazer o parse.
     */
    private String extractCountryCode(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "UNKNOWN";
        }
        try {
            // Adicionar o '+' na frente se não tiver, para ajudar a biblioteca no parse
            String fullNumber = phoneNumber.startsWith("+") ? phoneNumber : "+" + phoneNumber;
            
            // O segundo parâmetro é a "região padrão" para ajudar no parse se o número for ambíguo
            // Como esperamos E.164, a região padrão não é tão crítica, mas "BR" é um bom palpite para sua base.
            PhoneNumber parsedNumber = phoneUtil.parse(fullNumber, "BR");

            if (phoneUtil.isValidNumber(parsedNumber)) {
                return String.valueOf(parsedNumber.getCountryCode());
            } else {
                log.warn("Número de telefone '{}' considerado inválido pela libphonenumber.", phoneNumber);
                // Tenta uma extração manual como fallback
                if (fullNumber.length() > 11) { // Heurística simples
                    return fullNumber.substring(1, 3); // Ex: "+55..." -> "55"
                }
                return "UNKNOWN";
            }
        } catch (Exception e) {
            log.error("Erro ao fazer parse do número de telefone '{}' com libphonenumber: {}", phoneNumber, e.getMessage());
            return "UNKNOWN";
        }
    }

    private BillingPlan getAndResetPlanIfNeeded(Company company) {
        if (company == null) return null;
        BillingPlan plan = billingPlanRepository.findByCompany(company).orElse(null);
        if (plan != null) {
            resetCountersIfNeeded(plan);
        }
        return plan;
    }

    private void resetCountersIfNeeded(BillingPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        @SuppressWarnings("unused")
        boolean needsSave = false;
        if (plan.getLastDailyReset() == null || plan.getLastDailyReset().toLocalDate().isBefore(now.toLocalDate())) {
            plan.setCurrentDayMessagesSent(0);
            plan.setLastDailyReset(now.withHour(0).withMinute(0).withSecond(0).withNano(0));
            needsSave = true;
        }
        if (plan.getLastMonthlyReset() == null || YearMonth.from(plan.getLastMonthlyReset()).isBefore(YearMonth.from(now))) {
            resetAllCounters(plan, now);
            needsSave = true;
        }
        // O Hibernate Dirty Checking salvará se 'needsSave' for true, pois o método chamador é @Transactional
    }

    private void resetAllCounters(BillingPlan plan, LocalDateTime now) {
        log.debug("Resetando todos os contadores mensais para empresa ID {}", plan.getCompany().getId());
        plan.setCurrentDayMessagesSent(0);
        plan.setCurrentMonthMessagesSent(0);
        plan.setCurrentMonthCampaignsExecuted(0);
        plan.setCurrentMonthMetaCost(BigDecimal.ZERO);
        plan.setCurrentMonthPlatformFee(BigDecimal.ZERO);
        plan.setLastDailyReset(now.withHour(0).withMinute(0).withSecond(0).withNano(0));
        plan.setLastMonthlyReset(now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
    }
}
