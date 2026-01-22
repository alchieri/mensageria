package com.br.alchieri.consulting.mensageria.chat.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateComponentRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateParameterRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest.ParameterMapping;
import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest.TemplateComponentMapping;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateParameterGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Gera os componentes finais (TemplateComponentRequest) com parâmetros resolvidos
     * a partir de uma lista de regras de mapeamento e dos dados de contexto.
     *
     * @param mappings As regras de mapeamento.
     * @param contact O contato (pode ser nulo se não for a fonte).
     * @param company A empresa (pode ser nula se não for a fonte).
     * @param user O usuário (pode ser nulo se não for a fonte).
     * @return Uma lista de TemplateComponentRequest com os parâmetros preenchidos.
     */
    public List<TemplateComponentRequest> generateComponents(List<TemplateComponentMapping> mappings, Contact contact, Company company, User user) {
        if (mappings == null || mappings.isEmpty()) {
            return Collections.emptyList();
        }

        List<TemplateComponentRequest> generatedComponents = new ArrayList<>();
        try {
            for (TemplateComponentMapping compMapping : mappings) {
                String type = compMapping.getType().toLowerCase();
                
                // Lógica para HEADER e BODY
                if ("header".equals(type) || "body".equals(type)) {
                    List<TemplateParameterRequest> parameters = new ArrayList<>();
                    List<ParameterMapping> paramMappings = "header".equals(type) ?
                                                             compMapping.getHeaderParameters() :
                                                             compMapping.getBodyParameters();

                    if (paramMappings != null) {
                        for (ParameterMapping paramMapping : paramMappings) {
                            parameters.add(buildParameter(paramMapping, contact, company, user));
                        }
                    }

                    if (!parameters.isEmpty()) {
                        TemplateComponentRequest component = new TemplateComponentRequest();
                        component.setType(type);
                        component.setParameters(parameters);
                        generatedComponents.add(component);
                    }
                }
                // Lógica para BUTTONS
                else if ("buttons".equals(type)) {
                    if (compMapping.getButtonParameters() != null) {
                        for (ScheduleCampaignRequest.ButtonMapping buttonMapping : compMapping.getButtonParameters()) {
                            TemplateComponentRequest buttonComponent = new TemplateComponentRequest();
                            buttonComponent.setType("button");
                            buttonComponent.setSub_type("url");
                            buttonComponent.setIndex(String.valueOf(buttonMapping.getIndex()));

                            List<TemplateParameterRequest> buttonParams = new ArrayList<>();
                            for (ParameterMapping urlParamMapping : buttonMapping.getUrlParameters()) {
                                TemplateParameterRequest urlParam = new TemplateParameterRequest();
                                urlParam.setType("text");
                                Object value = resolveValue(urlParamMapping, contact, company, user);
                                urlParam.setText(String.valueOf(value != null ? value : ""));
                                buttonParams.add(urlParam);
                            }
                            buttonComponent.setParameters(buttonParams);
                            generatedComponents.add(buttonComponent);
                        }
                    }
                } else {
                    log.warn("Tipo de componente de mapeamento desconhecido: {}", type);
                }
            }
        } catch (Exception e) {
            log.error("Erro ao gerar componentes dinâmicos para contato ID {}: {}", (contact != null ? contact.getId() : "N/A"), e.getMessage());
            throw new BusinessException("Falha ao gerar parâmetros de template para contato " + (contact != null ? contact.getName() : "desconhecido"), e);
        }
        return generatedComponents;
    }

    /**
     * Gera os componentes finais a partir de um JSON de regras de mapeamento.
     */
    public List<TemplateComponentRequest> generateComponents(String componentMappingsJson, Contact contact, Company company, User user) throws IOException {
        if (!StringUtils.hasText(componentMappingsJson)) return Collections.emptyList();
        List<TemplateComponentMapping> mappings = objectMapper.readValue(
                componentMappingsJson, new TypeReference<>() {});
        return generateComponents(mappings, contact, company, user);
    }

    /**
     * Constrói um único parâmetro (TemplateParameterRequest) a partir de uma regra de mapeamento.
     */
    private TemplateParameterRequest buildParameter(ParameterMapping mapping, Contact contact, Company company, User user) {
        TemplateParameterRequest param = new TemplateParameterRequest();
        param.setType(mapping.getType());

        Object value = resolveValue(mapping, contact, company, user);

        switch (mapping.getType().toLowerCase()) {
            case "text":
                param.setText(String.valueOf(value != null ? value : ""));
                break;
            case "image":
            case "document":
            case "video":
                if (value == null) throw new BusinessException("Valor para parâmetro de mídia não pode ser nulo.");
                param.setMediaId(String.valueOf(value));
                break;
            case "currency":
                if (!(value instanceof Number)) {
                    throw new BusinessException("Valor para 'currency' deve ser numérico, mas foi: " + (value != null ? value.getClass().getName() : "null"));
                }
                BigDecimal amount = new BigDecimal(value.toString());
                long amount1000 = amount.multiply(new BigDecimal("1000")).longValue();
                String currencyCode = mapping.getCurrencyCode() != null ? mapping.getCurrencyCode() : "BRL";
                
                // Formatação de fallback
                NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("pt", "BR")); // Ajuste a localidade se necessário
                String fallbackValue = currencyFormatter.format(amount);

                param.setCurrency(new TemplateParameterRequest.CurrencyRequest(fallbackValue, currencyCode.toUpperCase(), amount1000));
                break;
            case "date_time":
                String formattedDate = formatDate(value, mapping.getDateTimeFormat());
                param.setDateTime(new TemplateParameterRequest.DateTimeRequest(formattedDate));
                break;
            default:
                throw new BusinessException("Tipo de parâmetro não suportado na geração dinâmica: " + mapping.getType());
        }
        return param;
    }

    /**
     * Determina o valor final do parâmetro com base na prioridade das fontes.
     */
    private Object resolveValue(ParameterMapping mapping, Contact contact, Company company, User user) {
        if (StringUtils.hasText(mapping.getFixedValue())) {
            return mapping.getFixedValue();
        }
        if (StringUtils.hasText(mapping.getPayloadValue())) {
            return mapping.getPayloadValue();
        }
        if (StringUtils.hasText(mapping.getSourceField())) {
            return resolveSourceField(mapping.getSourceField(), contact, company, user);
        }
        throw new BusinessException("Mapeamento de parâmetro inválido: nenhuma fonte de valor (fixedValue, payloadValue ou sourceField) foi fornecida.");
    }

    /**
     * Resolve o valor a partir de uma string de fonte como "contact.name" ou "company.contactEmail".
     */
    private Object resolveSourceField(String sourceField, Contact contact, Company company, User user) {
        if (!StringUtils.hasText(sourceField)) {
            return null;
        }
        
        String[] parts = sourceField.split("\\.", 2); // Divide em no máximo 2 partes (ex: "contact", "customFields.cidade")
        if (parts.length < 2) {
            throw new BusinessException("Formato de 'sourceField' inválido. Deve ser 'tipo.campo', ex: 'contact.name'. Fonte recebida: " + sourceField);
        }

        String sourceType = parts[0].toLowerCase();
        String fieldName = parts[1]; // Mantém o case aqui para campos customizados

        switch (sourceType) {
            case "contact":
                if (contact == null) {
                    throw new BusinessException("Fonte 'contact' solicitada, mas nenhum contexto de contato foi fornecido para o envio.");
                }
                return getContactFieldValue(contact, fieldName);
            case "company":
                if (company == null) {
                    throw new BusinessException("Fonte 'company' solicitada, mas nenhum contexto de empresa foi fornecido.");
                }
                return getCompanyFieldValue(company, fieldName);
            case "user":
                if (user == null) {
                    throw new BusinessException("Fonte 'user' solicitada, mas nenhum contexto de usuário foi fornecido.");
                }
                return getUserFieldValue(user, fieldName);
            default:
                throw new BusinessException("Tipo de fonte desconhecido na definição de mapeamento: '" + sourceType + "'. Use 'contact', 'company', ou 'user'.");
        }
    }
    
    // --- Métodos Getters específicos para cada entidade ---
    
    /**
     * Obtém o valor de um campo da entidade Contact.
     * @param contact A entidade Contact.
     * @param fieldPath O caminho do campo (ex: "name", "customFields.cidade_natal").
     * @return O valor do campo.
     */
    private Object getContactFieldValue(Contact contact, String fieldPath) {
        String[] parts = fieldPath.split("\\.", 2);
        String mainField = parts[0].toLowerCase();

        switch (mainField) {
            // Campos Padrão
            case "name": return contact.getName();
            case "phonenumber": return contact.getPhoneNumber();
            case "email": return contact.getEmail();
            case "dateofbirth": return contact.getDateOfBirth();
            case "gender": return contact.getGender();
            
            // Campos Profissionais
            case "companyname": return contact.getCompanyName();
            case "jobtitle": return contact.getJobTitle();
            case "department": return contact.getDepartment();
            
            // Campos de CRM
            case "leadscore": return contact.getLeadScore();
            case "leadsource": return contact.getLeadSource();

            // Campos de Endereço (se Address não for nulo)
            case "street": return (contact.getAddress() != null) ? contact.getAddress().getStreet() : null;
            case "addressnumber": return (contact.getAddress() != null) ? contact.getAddress().getNumber() : null; // "number" é palavra reservada em alguns contextos
            case "complement": return (contact.getAddress() != null) ? contact.getAddress().getComplement() : null;
            case "neighborhood": return (contact.getAddress() != null) ? contact.getAddress().getNeighborhood() : null;
            case "city": return (contact.getAddress() != null) ? contact.getAddress().getCity() : null;
            case "state": return (contact.getAddress() != null) ? contact.getAddress().getState() : null;
            case "postalcode": return (contact.getAddress() != null) ? contact.getAddress().getPostalCode() : null;
            case "country": return (contact.getAddress() != null) ? contact.getAddress().getCountry() : null;

            // Campos Customizados
            case "customfields":
                if (parts.length < 2) {
                    throw new BusinessException("Fonte 'contact.customFields' requer uma chave. Ex: 'contact.customFields.nome_da_chave'.");
                }
                String customFieldKey = parts[1];
                if (contact.getCustomFields() == null) {
                    return null; // ou ""
                }
                return contact.getCustomFields().get(customFieldKey);

            default:
                 // Fallback para tentar buscar em campos customizados se o nome não for padrão
                 if (contact.getCustomFields() != null && contact.getCustomFields().containsKey(fieldPath)) {
                    return contact.getCustomFields().get(fieldPath);
                }
                throw new BusinessException("Campo de contato desconhecido: '" + fieldPath + "'.");
        }
    }
    
    /**
     * Obtém o valor de um campo da entidade Company.
     */
    private Object getCompanyFieldValue(Company company, String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "name": return company.getName();
            case "documentnumber": return company.getDocumentNumber();
            case "contactemail": return company.getContactEmail();
            case "contactphonenumber": return company.getContactPhoneNumber();
            // Adicionar outros campos da empresa se forem relevantes para templates
            default:
                throw new BusinessException("Campo de empresa desconhecido: '" + fieldName + "'.");
        }
    }

    /**
     * Obtém o valor de um campo da entidade User.
     */
    private Object getUserFieldValue(User user, String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "fullname": return user.getFullName();
            case "email": return user.getEmail();
            case "username": return user.getUsername();
            // Adicionar outros campos do usuário se forem relevantes para templates
            default:
                throw new BusinessException("Campo de usuário desconhecido: '" + fieldName + "'.");
        }
    }
    
    private String formatDate(Object value, String format) {
        if (value == null) return "";
        if (value instanceof TemporalAccessor temporal) {
            try {
                DateTimeFormatter formatter = (StringUtils.hasText(format)) ? DateTimeFormatter.ofPattern(format) : DateTimeFormatter.ISO_LOCAL_DATE;
                return formatter.format(temporal);
            } catch (Exception e) {
                log.warn("Falha ao formatar data '{}' com o padrão '{}'. Usando toString().", value, format);
                return value.toString();
            }
        }
        return String.valueOf(value);
    }
}
