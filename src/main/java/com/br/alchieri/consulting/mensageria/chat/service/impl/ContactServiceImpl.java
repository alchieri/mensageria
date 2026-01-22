package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.chat.dto.response.CsvImportResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Tag;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.TagRepository;
import com.br.alchieri.consulting.mensageria.chat.service.ContactService;
import com.br.alchieri.consulting.mensageria.dto.request.ContactRequest;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final TagRepository tagRepository;

    @Override
    public Page<Contact> getContactsByCompany(Company company, Pageable pageable) {
        return contactRepository.findByCompany(company, pageable);
    }

    @Override
    public Optional<Contact> getContactByIdAndCompany(Long contactId, Company company) {
        return contactRepository.findByIdAndCompany(contactId, company);
    }

    @Override
    @Transactional
    public Contact createContact(ContactRequest request, Company company) {
        
        // 1. Normalização: Garante que estamos lidando com a versão "correta" (com 9 se for BR)
        String normalizedNumber = normalizePhoneNumber(request.getPhoneNumber());
        request.setPhoneNumber(normalizedNumber); // Atualiza o request para garantir que mapDtoToEntity use o normalizado

        // 2. Verificação Robusta: Procura por duplicatas testando variações (com/sem 9)
        checkIfPhoneNumberExists(company, normalizedNumber);

        Contact contact = new Contact();
        contact.setCompany(company);
        mapDtoToEntity(request, contact, company);
        return contactRepository.save(contact);
    }

    @Override
    @Transactional
    public Contact updateContact(Long contactId, ContactRequest request, Company company) {
        Contact contact = contactRepository.findByIdAndCompany(contactId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Contato com ID " + contactId + " não encontrado ou não pertence à sua empresa."));

        // Normaliza o novo número recebido
        String newNormalizedNumber = normalizePhoneNumber(request.getPhoneNumber());
        request.setPhoneNumber(newNormalizedNumber);

        // Verifica duplicidade apenas se o número mudou
        if (!contact.getPhoneNumber().equals(newNormalizedNumber)) {
             checkIfPhoneNumberExists(company, newNormalizedNumber);
        }

        mapDtoToEntity(request, contact, company);
        return contactRepository.save(contact);
    }

    @Override
    @Transactional
    public void deleteContact(Long contactId, Company company) {
        Contact contact = contactRepository.findByIdAndCompany(contactId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Contato com ID " + contactId + " não encontrado ou não pertence à sua empresa."));
        contactRepository.delete(contact);
    }

    @Override
    @Transactional
    public CsvImportResponse importContactsFromCsv(MultipartFile file, Company company) {
        if (file.isEmpty()) {
            throw new BusinessException("Arquivo CSV não pode estar vazio.");
        }
        if (!"text/csv".equals(file.getContentType())) {
             log.warn("Tipo de arquivo inválido para upload de contatos: {}", file.getContentType());
             throw new BusinessException("Arquivo inválido. Apenas arquivos CSV são permitidos.");
        }

        List<String> errors = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int rowCount = 0;

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // Mapeia colunas do CSV para os campos do DTO ContactRequest
            HeaderColumnNameMappingStrategy<ContactRequest> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(ContactRequest.class);
            // Opcional: Definir mapeamento explícito se os nomes das colunas não baterem
            // Map<String, String> columnMapping = new HashMap<>();
            // columnMapping.put("nome_contato", "name");
            // columnMapping.put("telefone", "phoneNumber");
            // strategy.setColumnMapping(columnMapping);

            CsvToBean<ContactRequest> csvToBean = new CsvToBeanBuilder<ContactRequest>(reader)
                    .withType(ContactRequest.class)
                    .withMappingStrategy(strategy)
                    .withSeparator(',') // ou ';' dependendo do CSV
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            for (ContactRequest contactDto : csvToBean) {
                rowCount++;
                try {
                    // 1. Normaliza o telefone vindo do CSV
                    String rawPhone = contactDto.getPhoneNumber();
                    String normalizedPhone = normalizePhoneNumber(rawPhone);
                    contactDto.setPhoneNumber(normalizedPhone);

                    // 2. Busca Inteligente (Upsert)
                    // Tenta achar pelo número exato (já normalizado) OU pela variação sem 9
                    Optional<Contact> existingContactOpt = findContactSmart(company, normalizedPhone);

                    if (existingContactOpt.isPresent()) {
                        // Atualiza
                        Contact existingContact = existingContactOpt.get();
                        mapDtoToEntity(contactDto, existingContact, company);
                        contactRepository.save(existingContact);
                        updatedCount++;
                    } else {
                        // Cria
                        Contact newContact = new Contact();
                        newContact.setCompany(company);
                        mapDtoToEntity(contactDto, newContact, company);
                        contactRepository.save(newContact);
                        createdCount++;
                    }
                } catch (Exception e) {
                    // Captura erro de validação ou do banco para uma linha específica
                    log.warn("Falha ao importar linha {}: {}", rowCount, e.getMessage());
                    errors.add("Linha " + rowCount + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erro fatal ao processar arquivo CSV para empresa ID {}", company.getId(), e);
            throw new BusinessException("Erro ao ler o arquivo CSV. Verifique o formato e o conteúdo. Erro: " + e.getMessage());
        }

        return CsvImportResponse.builder()
                .totalRows(rowCount)
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }


    // Método helper para mapear DTO para Entidade
    private void mapDtoToEntity(ContactRequest dto, Contact entity, Company company) {
        
        // Mapeia informações pessoais e de contato
        entity.setName(dto.getName());
        entity.setPhoneNumber(dto.getPhoneNumber());
        entity.setEmail(dto.getEmail());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setGender(dto.getGender());

        // Mapeia informações profissionais
        entity.setCompanyName(dto.getCompanyName());
        entity.setJobTitle(dto.getJobTitle());
        entity.setDepartment(dto.getDepartment());

        // Mapeia o endereço usando o AddressDTO
        if (dto.getAddress() != null) {
            // Reutiliza o método .toEntity() do DTO para converter para a entidade Address
            entity.setAddress(dto.getAddress().toEntity());
        } else {
            // Se o endereço no DTO for nulo, remove o endereço da entidade
            entity.setAddress(null);
        }

        // Mapeia preferências e status
        // Usar a verificação de nulo é importante para updates parciais,
        // onde o cliente pode não enviar todos os campos.
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getPreferredLanguage() != null) {
            entity.setPreferredLanguage(dto.getPreferredLanguage());
        }
        if (dto.getTimeZone() != null) {
            entity.setTimeZone(dto.getTimeZone());
        }
        if (dto.getIsVip() != null) {
            entity.setVip(dto.getIsVip());
        }
        if (dto.getAllowMarketingMessages() != null) {
            entity.setAllowMarketingMessages(dto.getAllowMarketingMessages());
        }
        if (dto.getAllowNotifications() != null) {
            entity.setAllowNotifications(dto.getAllowNotifications());
        }

        // Mapeia dados de CRM/Marketing
        if (dto.getLeadSource() != null) {
            entity.setLeadSource(dto.getLeadSource());
        }
        if (dto.getLeadScore() != null) {
            entity.setLeadScore(dto.getLeadScore());
        }

        // Mapeia observações
        entity.setNotes(dto.getNotes());

        // Lógica para sincronizar as tags
        if (dto.getTags() == null) {
            // Se a lista de tags no DTO for nula, não fazemos nada (mantém as tags existentes).
            // Se você quiser que `null` signifique "remover todas as tags", mude para: entity.getTags().clear();
        } else {
            // Se a lista de tags for fornecida (mesmo que vazia), sincronizamos.
            // Isso permite remover todas as tags enviando um array vazio [].
            
            // Remove tags que não estão mais no request
            entity.getTags().removeIf(tag -> !dto.getTags().contains(tag.getName()));

            // Adiciona novas tags
            Set<String> existingTagNamesInEntity = entity.getTags().stream()
                                                        .map(Tag::getName)
                                                        .collect(Collectors.toSet());

            for (String tagName : dto.getTags()) {
                if (!existingTagNamesInEntity.contains(tagName.trim())) {
                    // Busca a tag no banco ou cria uma nova se não existir para esta empresa
                    Tag tag = tagRepository.findByCompanyAndNameIgnoreCase(company, tagName.trim())
                            .orElseGet(() -> {
                                log.info("Criando nova tag '{}' para a empresa ID {}.", tagName.trim(), company.getId());
                                // O CascadeType.PERSIST no relacionamento @ManyToMany cuidará de salvar a nova tag
                                // quando o contato for salvo.
                                return new Tag(tagName.trim(), company);
                            });
                    entity.getTags().add(tag);
                }
            }
        }
    }

    /**
     * Padroniza números de celular do Brasil para ter sempre 13 dígitos (55 + DDD + 9 + 8 números).
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        // Remove caracteres não numéricos apenas por segurança
        String cleaned = phoneNumber.replaceAll("\\D", "");
        
        // Se for Brasil (começa com 55) e tiver 12 dígitos (falta o 9), adiciona.
        if (cleaned.startsWith("55") && cleaned.length() == 12) {
            String normalized = cleaned.substring(0, 4) + "9" + cleaned.substring(4);
            log.debug("Normalizando telefone: {} -> {}", cleaned, normalized);
            return normalized;
        }
        return cleaned;
    }

    /**
     * Verifica se o telefone já existe na empresa, considerando variações do 9º dígito.
     * Lança exceção se encontrar.
     */
    private void checkIfPhoneNumberExists(Company company, String normalizedPhoneNumber) {
        // Busca direta pelo número (já normalizado)
        if (contactRepository.findByCompanyAndPhoneNumber(company, normalizedPhoneNumber).isPresent()) {
            throw new BusinessException("Contato com o número '" + normalizedPhoneNumber + "' já existe para esta empresa.");
        }

        // Se for celular BR (13 digitos), verifica se existe a versão SEM o 9 no banco (legado)
        if (normalizedPhoneNumber.startsWith("55") && normalizedPhoneNumber.length() == 13) {
            String numberWithoutNine = normalizedPhoneNumber.substring(0, 4) + normalizedPhoneNumber.substring(5);
            if (contactRepository.findByCompanyAndPhoneNumber(company, numberWithoutNine).isPresent()) {
                throw new BusinessException("Contato com o número '" + numberWithoutNine + "' (variação sem 9º dígito) já existe.");
            }
        }
    }

    /**
     * Tenta encontrar um contato existente buscando pelo número normalizado E suas variações.
     * Usado na importação CSV para fazer o "match" correto.
     */
    private Optional<Contact> findContactSmart(Company company, String normalizedPhoneNumber) {
        // Tenta exato
        Optional<Contact> exact = contactRepository.findByCompanyAndPhoneNumber(company, normalizedPhoneNumber);
        if (exact.isPresent()) return exact;

        // Se for celular BR, tenta a versão sem o 9 (caso o banco tenha dados antigos)
        if (normalizedPhoneNumber.startsWith("55") && normalizedPhoneNumber.length() == 13) {
            String numberWithoutNine = normalizedPhoneNumber.substring(0, 4) + normalizedPhoneNumber.substring(5);
            return contactRepository.findByCompanyAndPhoneNumber(company, numberWithoutNine);
        }
        
        return Optional.empty();
    }
}
