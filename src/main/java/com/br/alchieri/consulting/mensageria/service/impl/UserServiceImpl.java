package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyCreateUserRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.AdminCreateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.AdminUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.RegisterUserRequest;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User registerNewUser(RegisterUserRequest request, Long companyIdForAssociation, Set<Role> defaultRoles) {
        
        log.info("Tentativa de registro de usuário: {}", request.getUsername());
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("Nome de usuário '" + request.getUsername() + "' já existe.");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email '" + request.getEmail() + "' já está em uso.");
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setFullName(request.getFullName());
        newUser.setEmail(request.getEmail());
        newUser.setEnabled(true);

        if (companyIdForAssociation != null) {
            Company company = companyRepository.findById(companyIdForAssociation)
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyIdForAssociation + " não encontrada para associação do usuário."));
            newUser.setCompany(company);
            log.info("Usuário {} associado à empresa {}", request.getUsername(), company.getName());
        } else {
            log.info("Usuário {} registrado sem associação a uma empresa (provavelmente um admin BSP).", request.getUsername());
        }

        newUser.setRoles(defaultRoles != null && !defaultRoles.isEmpty() ? defaultRoles : Set.of(Role.ROLE_USER));
        log.info("Roles atribuídas a {}: {}", request.getUsername(), newUser.getRoles());

        return userRepository.save(newUser);
    }

    @Override
    public Optional<User> findById(Long userId) {
        // A lógica de permissão (se o chamador pode ver este usuário)
        // será tratada no controller ou em uma camada de segurança de método,
        // mantendo o serviço focado na busca.
        log.debug("Buscando usuário por ID: {}", userId);
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Page<User> findAllUsers(Pageable pageable) {
        
        User currentUser = getAuthenticatedUserFromSecurityContext();
        if (currentUser == null || !currentUser.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            throw new AccessDeniedException("Apenas administradores BSP podem listar todos os usuários.");
        }
        log.info("Admin BSP {} listando todos os usuários.", currentUser.getUsername());
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<User> findUsersByCompany(Long companyId, Pageable pageable) {
        
        User currentUser = getAuthenticatedUserFromSecurityContext();
        if (currentUser == null) {
            throw new AccessDeniedException("Autenticação necessária.");
        }

        Company targetCompany = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));

        // BSP Admin pode listar usuários de qualquer empresa
        // Company Admin pode listar usuários da sua própria empresa
        if (currentUser.getRoles().contains(Role.ROLE_BSP_ADMIN) ||
            (currentUser.getRoles().contains(Role.ROLE_COMPANY_ADMIN) &&
             currentUser.getCompany() != null &&
             currentUser.getCompany().getId().equals(companyId))) {
            log.info("Usuário {} listando usuários da empresa {}", currentUser.getUsername(), targetCompany.getName());
            return userRepository.findByCompany(targetCompany, pageable);
        } else {
            throw new AccessDeniedException("Permissão negada para listar usuários desta empresa.");
        }
    }

    @Override
    @Transactional
    public User updateUserByAdmin(Long userId, AdminUpdateUserRequest request) {
        
        User adminUser = getAuthenticatedUserFromSecurityContext(); // Quem está fazendo a alteração
        if (adminUser == null || !adminUser.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            // TODO: Permitir que COMPANY_ADMIN altere usuários da sua própria empresa, mas com menos campos.
            throw new AccessDeniedException("Apenas administradores BSP podem atualizar usuários desta forma.");
        }

        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + userId + " não encontrado."));

        log.info("Admin BSP {} atualizando usuário ID {}", adminUser.getUsername(), userId);

        if (StringUtils.hasText(request.getUsername()) && !userToUpdate.getUsername().equals(request.getUsername())) {
            userRepository.findByUsername(request.getUsername()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId))
                    throw new BusinessException("Novo nome de usuário '" + request.getUsername() + "' já está em uso.");
            });
            userToUpdate.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getPassword())) {
            userToUpdate.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (StringUtils.hasText(request.getFullName())) {
            userToUpdate.setFullName(request.getFullName());
        }
        if (StringUtils.hasText(request.getEmail()) && !userToUpdate.getEmail().equals(request.getEmail())) {
             userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
                 if (!existingUser.getId().equals(userId))
                    throw new BusinessException("Novo email '" + request.getEmail() + "' já está em uso.");
             });
            userToUpdate.setEmail(request.getEmail());
        }
        if (request.getEnabled() != null) {
            userToUpdate.setEnabled(request.getEnabled());
        }
        if (request.getCompanyId() != null) {
            if (userToUpdate.getCompany() == null || !userToUpdate.getCompany().getId().equals(request.getCompanyId())) {
                Company company = companyRepository.findById(request.getCompanyId())
                        .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + request.getCompanyId() + " não encontrada para associação."));
                userToUpdate.setCompany(company);
            }
        } else if (request.getCompanyId() == null && userToUpdate.getCompany() != null) {
            // Se companyId é explicitamente nulo no request, desassocia (cuidado com órfãos)
            // userToUpdate.setCompany(null);
             log.warn("Tentativa de desassociar empresa do usuário {} via update. Verifique a intenção.", userId);
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            // TODO: Adicionar validação para garantir que um BSP_ADMIN não remova sua própria role de BSP_ADMIN acidentalmente,
            // ou que não atribua ROLE_BSP_ADMIN para usuários não autorizados.
            userToUpdate.setRoles(request.getRoles());
        }

        return userRepository.save(userToUpdate);
    }

    // Helper para pegar o usuário autenticado (pode ser movido para uma classe util de segurança)
    private User getAuthenticatedUserFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails)) {
            return null;
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        if (userDetails instanceof User) { // Se o principal já é nossa entidade User
            return (User) userDetails;
        }
        // Se não for, busca pelo username (acontece se UserDetails é o do Spring)
        return userRepository.findByUsername(userDetails.getUsername()).orElse(null);
    }

    // --- NOVOS MÉTODOS PARA COMPANY ADMIN ---

    @Override
    @Transactional
    public User createUserForCompany(CompanyCreateUserRequest request, Company company) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("Nome de usuário '" + request.getUsername() + "' já existe.");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email '" + request.getEmail() + "' já está em uso.");
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setFullName(request.getFullName());
        newUser.setEmail(request.getEmail());
        newUser.setEnabled(true);
        newUser.setCompany(company); // Associa à empresa do admin que está criando

        // Valida as roles: um Company Admin só pode criar USERs ou outros COMPANY_ADMINs
        Set<Role> rolesToSet = request.getRoles();
        if (rolesToSet == null || rolesToSet.isEmpty()) {
            rolesToSet = Set.of(Role.ROLE_USER); // Padrão
        }
        if (rolesToSet.contains(Role.ROLE_BSP_ADMIN)) {
            throw new AccessDeniedException("Administradores de empresa não podem criar administradores BSP.");
        }
        // Garante que todo usuário criado tenha pelo menos a role USER
        rolesToSet.add(Role.ROLE_USER);
        newUser.setRoles(rolesToSet);

        return userRepository.save(newUser);
    }

    @Override
    @Transactional
    public User updateUserInCompany(Long userId, CompanyUpdateUserRequest request, Company companyOfAdmin) {
        // 1. Busca o usuário a ser editado
        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + userId + " não encontrado."));
        
        // 2. Validação de Segurança Crucial:
        // Garante que o usuário a ser editado pertence à mesma empresa do administrador que está fazendo a requisição.
        if (userToUpdate.getCompany() == null || !Objects.equals(userToUpdate.getCompany().getId(), companyOfAdmin.getId())) {
            // Loga a tentativa de acesso indevido
            log.warn("Acesso negado: Administrador da empresa ID {} tentou editar usuário ID {} que pertence à empresa ID {}.",
                     companyOfAdmin.getId(), userId,
                     userToUpdate.getCompany() != null ? userToUpdate.getCompany().getId() : "N/A");
            throw new AccessDeniedException("Permissão negada para editar este usuário.");
        }

        // 3. Atualiza os campos se eles foram fornecidos no request
        
        // Atualização de Username
        if (StringUtils.hasText(request.getUsername()) && !userToUpdate.getUsername().equals(request.getUsername())) {
            // Verifica se o novo username já está em uso por outro usuário
            userRepository.findByUsername(request.getUsername()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) { // Garante que não é o mesmo usuário
                    throw new BusinessException("Novo nome de usuário '" + request.getUsername() + "' já está em uso.");
                }
            });
            userToUpdate.setUsername(request.getUsername());
        }

        // Atualização de Senha
        if (StringUtils.hasText(request.getPassword())) {
            userToUpdate.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Atualização de Nome Completo
        if (StringUtils.hasText(request.getFullName())) {
            userToUpdate.setFullName(request.getFullName());
        }

        // Atualização de Email
        if (StringUtils.hasText(request.getEmail()) && !userToUpdate.getEmail().equals(request.getEmail())) {
            // Verifica se o novo email já está em uso por outro usuário
            userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
                 if (!existingUser.getId().equals(userId)) {
                    throw new BusinessException("Novo email '" + request.getEmail() + "' já está em uso.");
                }
            });
            userToUpdate.setEmail(request.getEmail());
        }
        
        // Atualização do Status (Enabled/Disabled)
        if (request.getEnabled() != null) {
            // Lógica de negócio: impedir que um admin desative a si mesmo
            if (userToUpdate.getId().equals(getAuthenticatedUser().getId()) && !request.getEnabled()) {
                throw new BusinessException("Você não pode desativar sua própria conta.");
            }
            userToUpdate.setEnabled(request.getEnabled());
        }

        // Atualização das Roles
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            // Validação de Segurança: Um COMPANY_ADMIN não pode atribuir a role BSP_ADMIN
            if (request.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
                throw new AccessDeniedException("Administradores de empresa não podem atribuir a role de administrador BSP.");
            }
            
            // Lógica de negócio: impedir que o último COMPANY_ADMIN remova sua própria role de admin
            if (userToUpdate.getRoles().contains(Role.ROLE_COMPANY_ADMIN) && !request.getRoles().contains(Role.ROLE_COMPANY_ADMIN)) {
                 long adminCount = userRepository.countByCompanyAndRolesContaining(companyOfAdmin, Role.ROLE_COMPANY_ADMIN);
                 if (adminCount <= 1) {
                     throw new BusinessException("Não é possível remover a role de administrador do último admin da empresa.");
                 }
            }

            // Garante que todo usuário mantenha a role USER básica
            Set<Role> newRoles = request.getRoles().stream().collect(Collectors.toSet()); // Cria uma cópia mutável
            newRoles.add(Role.ROLE_USER);
            userToUpdate.setRoles(newRoles);
        }

        // 4. Salva a entidade atualizada no banco
        return userRepository.save(userToUpdate);
    }

    @Override
    @Transactional
    public User createUserByAdmin(AdminCreateUserRequest request) {
        // Valida se username e email já existem
        userRepository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new BusinessException("Nome de usuário '" + request.getUsername() + "' já existe.");
        });
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            throw new BusinessException("Email '" + request.getEmail() + "' já está em uso.");
        });

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        String fullName = (request.getFirstName().trim() + " " + request.getLastName().trim()).trim();
        newUser.setFullName(fullName);
        newUser.setEmail(request.getEmail());
        newUser.setEnabled(true);
        newUser.setRoles(request.getRoles());

        if (request.getCompanyId() != null && request.getCompanyId() > 0) {
            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + request.getCompanyId() + " não encontrada."));
            newUser.setCompany(company);
        }

        log.info("Admin está criando um novo usuário: {}", newUser.getUsername());
        return userRepository.save(newUser);
    }

    @Override
    @Transactional
    public void deleteUserInCompany(Long userId, Company company) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + userId + " não encontrado."));

        if (user.getCompany() == null || !user.getCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("Permissão negada para deletar este usuário.");
        }
        
        // Lógica de negócio: impedir que um admin se delete ou delete o único admin
        if (user.getRoles().contains(Role.ROLE_COMPANY_ADMIN)) {
             long adminCount = userRepository.countByCompanyAndRolesContaining(company, Role.ROLE_COMPANY_ADMIN);
             if (adminCount <= 1) {
                 throw new BusinessException("Não é possível remover o último administrador da empresa.");
             }
        }

        userRepository.delete(user);
    }

    // Este método helper seria útil na classe para a lógica de 'não se desativar'
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new BusinessException("Contexto de segurança não encontrado.");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado no sistema."));
    }
}
