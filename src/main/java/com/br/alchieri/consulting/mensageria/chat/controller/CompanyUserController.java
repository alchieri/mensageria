package com.br.alchieri.consulting.mensageria.chat.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyCreateUserRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.response.UserInfoResponse;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.UserService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/company/users")
@Tag(name = "User Management (Company Admin)", description = "Endpoints para administradores de empresa gerenciarem seus próprios usuários.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')") // Protege todos os endpoints
@RequiredArgsConstructor
public class CompanyUserController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Criar Usuário na Empresa")
    public ResponseEntity<UserInfoResponse> createUser(@Valid @RequestBody CompanyCreateUserRequest request) {
        User currentUserAdmin = securityUtils.getAuthenticatedUser();
        User newUser = userService.createUserForCompany(request, currentUserAdmin.getCompany());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserInfoResponse.fromEntity(newUser));
    }

    @GetMapping
    @Operation(summary = "Listar Usuários da Empresa")
    public ResponseEntity<Page<UserInfoResponse>> listUsers(@ParameterObject Pageable pageable) {
        User currentUserAdmin = securityUtils.getAuthenticatedUser();
        Page<User> userPage = userService.findUsersByCompany(currentUserAdmin.getCompany().getId(), pageable);
        return ResponseEntity.ok(userPage.map(UserInfoResponse::fromEntity));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Obter Usuário da Empresa por ID")
    public ResponseEntity<UserInfoResponse> getUserById(@PathVariable Long userId) {
        User currentUserAdmin = securityUtils.getAuthenticatedUser();
        User user = userService.findById(userId).orElseThrow(() -> new com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException("Usuário não encontrado."));
        
        // Validação extra para garantir que está buscando um usuário da mesma empresa
        if (user.getCompany() == null || !user.getCompany().getId().equals(currentUserAdmin.getCompany().getId())) {
             throw new org.springframework.security.access.AccessDeniedException("Acesso negado.");
        }
        return ResponseEntity.ok(UserInfoResponse.fromEntity(user));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Atualizar Usuário na Empresa")
    public ResponseEntity<UserInfoResponse> updateUser(@PathVariable Long userId, @Valid @RequestBody CompanyUpdateUserRequest request) {
        User currentUserAdmin = securityUtils.getAuthenticatedUser();
        User updatedUser = userService.updateUserInCompany(userId, request, currentUserAdmin.getCompany());
        return ResponseEntity.ok(UserInfoResponse.fromEntity(updatedUser));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Excluir Usuário da Empresa")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        User currentUserAdmin = securityUtils.getAuthenticatedUser();
        userService.deleteUserInCompany(userId, currentUserAdmin.getCompany());
        return ResponseEntity.noContent().build();
    }
}
