package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.br.alchieri.consulting.mensageria.dto.request.CreateCompanyRequest;
import com.br.alchieri.consulting.mensageria.dto.request.UpdateCallbacksRequest;
import com.br.alchieri.consulting.mensageria.dto.request.UpdateCompanyRequest;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Address;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.service.CompanyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Company createCompany(CreateCompanyRequest request, User creator) {
        
        if (!creator.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            throw new AccessDeniedException("Apenas administradores BSP podem criar novas empresas.");
        }
        companyRepository.findByName(request.getName()).ifPresent(c -> {
            throw new BusinessException("Empresa com nome '" + request.getName() + "' já existe.");
        });
        if (StringUtils.hasText(request.getDocumentNumber())) {
            companyRepository.findByDocumentNumber(request.getDocumentNumber()).ifPresent(c -> { // Adicionar findByDocumentNumber no repo
                throw new BusinessException("Empresa com documento '" + request.getDocumentNumber() + "' já existe.");
            });
        }
        if (StringUtils.hasText(request.getMetaWabaId())) {
            companyRepository.findByMetaWabaId(request.getMetaWabaId()).ifPresent(c -> {
                throw new BusinessException("Meta WABA ID '" + request.getMetaWabaId() + "' já está associado a outra empresa.");
            });
        }


        Company company = new Company();
        company.setName(request.getName());
        company.setDocumentNumber(request.getDocumentNumber());

        if (request.getAddress() != null) {
            Address address = new Address();
            address.setStreet(request.getAddress().getStreet());
            address.setNumber(request.getAddress().getNumber());
            address.setComplement(request.getAddress().getComplement());
            address.setNeighborhood(request.getAddress().getNeighborhood());
            address.setCity(request.getAddress().getCity());
            address.setState(request.getAddress().getState());
            address.setPostalCode(request.getAddress().getPostalCode());
            address.setCountry(StringUtils.hasText(request.getAddress().getCountry()) ? request.getAddress().getCountry() : "Brasil");
            company.setAddress(address);
        }

        company.setContactEmail(request.getContactEmail());
        company.setContactPhoneNumber(request.getContactPhoneNumber());
        company.setMetaWabaId(request.getMetaWabaId());
        company.setMetaPrimaryPhoneNumberId(request.getMetaPrimaryPhoneNumberId());
        company.setFacebookBusinessManagerId(request.getFacebookBusinessManagerId());
        company.setGeneralCallbackUrl(request.getCallbackUrl()); // Ajustado nome do campo
        company.setTemplateStatusCallbackUrl(request.getTemplateStatusCallbackUrl());
        company.setOnboardingStatus(request.getOnboardingStatus() != null ? request.getOnboardingStatus() : com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus.NOT_STARTED);
        company.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        log.info("Admin BSP {} criando nova empresa: {}", creator.getUsername(), company.getName());
        return companyRepository.save(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findById(Long companyId) {
        
        return companyRepository.findByIdWithDetails(companyId);
    }

    @Override
    public Page<Company> findAllCompanies(Pageable pageable) {
        // TODO: Adicionar checagem se o chamador é ROLE_BSP_ADMIN
        return companyRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public Company updateCompany(Long companyId, UpdateCompanyRequest request, User updater) {
        
        // BSP_ADMIN pode atualizar qualquer empresa.
        // COMPANY_ADMIN pode atualizar apenas sua própria empresa.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));

        if (!updater.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            if (!updater.getRoles().contains(Role.ROLE_COMPANY_ADMIN) ||
                updater.getCompany() == null ||
                !updater.getCompany().getId().equals(companyId)) {
                throw new AccessDeniedException("Permissão negada para atualizar esta empresa.");
            }
        }
        log.info("Usuário {} atualizando empresa ID {}", updater.getUsername(), companyId);

        if (StringUtils.hasText(request.getName()) && !company.getName().equals(request.getName())) {
            companyRepository.findByName(request.getName()).ifPresent(existing -> {
                if (!existing.getId().equals(companyId))
                    throw new BusinessException("Nome de empresa '" + request.getName() + "' já está em uso.");
            });
            company.setName(request.getName());
        }
        if (StringUtils.hasText(request.getDocumentNumber())) company.setDocumentNumber(request.getDocumentNumber());
        if (StringUtils.hasText(request.getContactEmail())) company.setContactEmail(request.getContactEmail());
        if (StringUtils.hasText(request.getContactPhoneNumber())) company.setContactPhoneNumber(request.getContactPhoneNumber());

        if (request.getAddress() != null) {
            Address address = company.getAddress() == null ? new Address() : company.getAddress();
            // Mapear request.getAddress() para address...
            company.setAddress(address);
        }

        if (StringUtils.hasText(request.getMetaWabaId()) && (company.getMetaWabaId() == null || !company.getMetaWabaId().equals(request.getMetaWabaId()))) {
            companyRepository.findByMetaWabaId(request.getMetaWabaId()).ifPresent(existing -> {
                 if (!existing.getId().equals(companyId))
                    throw new BusinessException("Meta WABA ID '" + request.getMetaWabaId() + "' já está em uso.");
            });
            company.setMetaWabaId(request.getMetaWabaId());
        }
        if (StringUtils.hasText(request.getMetaPrimaryPhoneNumberId())) company.setMetaPrimaryPhoneNumberId(request.getMetaPrimaryPhoneNumberId());
        if (StringUtils.hasText(request.getFacebookBusinessManagerId())) company.setFacebookBusinessManagerId(request.getFacebookBusinessManagerId());
        if (StringUtils.hasText(request.getCallbackUrl())) company.setGeneralCallbackUrl(request.getCallbackUrl());
        if (StringUtils.hasText(request.getTemplateStatusCallbackUrl())) company.setTemplateStatusCallbackUrl(request.getTemplateStatusCallbackUrl());
        if (request.getOnboardingStatus() != null) company.setOnboardingStatus(request.getOnboardingStatus());
        if (request.getEnabled() != null) company.setEnabled(request.getEnabled());

        return companyRepository.save(company);
    }

    @Override
    public Company getCompanyOfUser(User user) {
        
        if (user == null) {
            throw new BusinessException("Usuário não pode ser nulo.");
        }
        // BSP_ADMIN não está necessariamente ligado a uma empresa, mas pode operar em todas.
        if (user.getRoles().contains(Role.ROLE_BSP_ADMIN) && user.getCompany() == null) {
            return null; // Ou uma representação de "todas as empresas" se a lógica exigir
        }
        if (user.getCompany() == null) {
            throw new BusinessException("Usuário " + user.getUsername() + " não está associado a uma empresa.");
        }
        return user.getCompany();
    }

    @Override
    @Transactional
    public User addUserToCompany(Long companyId, String usernameOfUserToAdd, Role roleForUserInCompany, User administrator) {
        
        if (! (administrator.getRoles().contains(Role.ROLE_BSP_ADMIN) ||
                (administrator.getRoles().contains(Role.ROLE_COMPANY_ADMIN) &&
                administrator.getCompany() != null &&
                administrator.getCompany().getId().equals(companyId))) ) {
            throw new AccessDeniedException("Permissão negada para adicionar usuário à empresa.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));
        User userToAdd = userRepository.findByUsername(usernameOfUserToAdd)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com username '" + usernameOfUserToAdd + "' não encontrado."));

        if (userToAdd.getCompany() != null && !userToAdd.getCompany().getId().equals(companyId)) {
            throw new BusinessException("Usuário já pertence a outra empresa.");
        }
        if (userToAdd.getCompany() != null && userToAdd.getCompany().getId().equals(companyId)) {
                log.info("Usuário {} já pertence à empresa {}. Atualizando roles.", usernameOfUserToAdd, companyId);
        } else {
            userToAdd.setCompany(company);
        }

        Set<Role> roles = new HashSet<>(userToAdd.getRoles());
        // Limpa roles antigas relacionadas a empresa se ele estava em outra (ou nenhuma).
        // Garante que não haja roles de BSP_ADMIN atribuídas por um COMPANY_ADMIN.
        if (roleForUserInCompany == Role.ROLE_COMPANY_ADMIN || roleForUserInCompany == Role.ROLE_USER) {
            roles.remove(Role.ROLE_COMPANY_ADMIN); // Remove para re-adicionar ou definir como USER
            roles.add(roleForUserInCompany);
        } else if (roleForUserInCompany != null && administrator.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            roles.add(roleForUserInCompany); // BSP_ADMIN pode adicionar outras roles
        } else if (roleForUserInCompany != null) {
            throw new AccessDeniedException("Permissão negada para atribuir role: " + roleForUserInCompany);
        }

        roles.add(Role.ROLE_USER); // Todo usuário de empresa é pelo menos ROLE_USER
        userToAdd.setRoles(roles);

        return userRepository.save(userToAdd);
    }

    @Override
    @Transactional
    public void removeUserFromCompany(Long companyId, Long userIdToRemove, User administrator) {
        
        if (! (administrator.getRoles().contains(Role.ROLE_BSP_ADMIN) ||
                (administrator.getRoles().contains(Role.ROLE_COMPANY_ADMIN) &&
                administrator.getCompany() != null &&
                administrator.getCompany().getId().equals(companyId))) ) {
            throw new AccessDeniedException("Permissão negada para remover usuário da empresa.");
        }

        User userToRemove = userRepository.findById(userIdToRemove)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + userIdToRemove + " não encontrado."));

        if (userToRemove.getCompany() == null || !userToRemove.getCompany().getId().equals(companyId)) {
            throw new BusinessException("Usuário não pertence a esta empresa.");
        }
        // Adicionar lógica de negócio: Não permitir remover o último COMPANY_ADMIN?
        // Ou se um COMPANY_ADMIN está tentando remover outro COMPANY_ADMIN (apenas BSP_ADMIN poderia?)

        userToRemove.setCompany(null);
        // Ao remover da empresa, pode-se limpar as roles específicas da empresa
        userToRemove.getRoles().remove(Role.ROLE_COMPANY_ADMIN);
        userToRemove.getRoles().remove(Role.ROLE_USER); // Se ele não deve ter mais acesso algum.
                                                            // Se ele ainda é um usuário da plataforma mas sem empresa,
                                                            // talvez manter ROLE_USER ou uma role específica.
        if (userToRemove.getRoles().isEmpty()) { // Se ficou sem roles, dar uma padrão ou desabilitar
            userToRemove.setEnabled(false); // Exemplo: desabilitar usuário sem roles/empresa
        }

        userRepository.save(userToRemove);
        log.info("Usuário ID {} removido da Empresa ID {}.", userIdToRemove, companyId);
    }

    @Override
    @Transactional
    public Company updateCompanyCallbacks(Company company, UpdateCallbacksRequest request) {
        log.info("Atualizando URLs de callback para a empresa ID: {}", company.getId());

        boolean updated = false;

        // Atualiza a URL geral se for fornecida no request
        if (request.getCallbackUrl() != null) {
            log.debug("Atualizando generalCallbackUrl para: {}", request.getCallbackUrl());
            company.setGeneralCallbackUrl(request.getCallbackUrl().isBlank() ? null : request.getCallbackUrl());
            updated = true;
        }

        // Atualiza a URL de status de template se for fornecida
        if (request.getTemplateStatusCallbackUrl() != null) {
            log.debug("Atualizando templateStatusCallbackUrl para: {}", request.getTemplateStatusCallbackUrl());
            company.setTemplateStatusCallbackUrl(request.getTemplateStatusCallbackUrl().isBlank() ? null : request.getTemplateStatusCallbackUrl());
            updated = true;
        }

        // Adicionar lógica para outras URLs de callback aqui...

        if (updated) {
            return companyRepository.save(company);
        } else {
            // Se nenhum campo foi fornecido para atualização, apenas retorna a entidade sem salvar
            return company;
        }
    }
}
