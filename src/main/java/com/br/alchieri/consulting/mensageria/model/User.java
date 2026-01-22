package com.br.alchieri.consulting.mensageria.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.br.alchieri.consulting.mensageria.model.enums.Role;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = { // Adicionar índice no novo campo
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_email", columnList = "email", unique = true)
    }
)
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    @Email
    private String email; // Email do usuário, geralmente para login ou contato

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.EAGER) // Muitos usuários para uma empresa
    @JoinColumn(name = "company_id", nullable = true) // Chave estrangeira para a tabela companies
    private Company company;

    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER) // Carrega as roles junto com o User
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new HashSet<>(); // Usar Set para evitar duplicatas

    // --- Mapeamento UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        
        // Mapeia as Roles da entidade para GrantedAuthority do Spring Security
        if (roles == null || roles.isEmpty())
            return List.of(new SimpleGrantedAuthority(Role.ROLE_USER.name())); // Default para USER se não houver roles
        return roles.stream().map(role -> new SimpleGrantedAuthority(role.name())).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Adicione lógica se necessário
    }

    @Override
    public boolean isAccountNonLocked() {
         return true; // Adicione lógica se necessário
    }

    @Override
    public boolean isCredentialsNonExpired() {
         return true; // Adicione lógica se necessário
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
