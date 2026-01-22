package com.br.alchieri.consulting.mensageria.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new BusinessException("Nenhum usuário autenticado encontrado. Acesso negado.");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Se o UserDetails já é a nossa entidade User, ótimo. Senão, busca no repositório.
        if (userDetails instanceof User) {
            return (User) userDetails;
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado no sistema."));
    }
}
