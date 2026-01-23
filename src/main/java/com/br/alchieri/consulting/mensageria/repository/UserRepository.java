package com.br.alchieri.consulting.mensageria.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Método para buscar cliente pelo username (usado pelo Spring Security)
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Page<User> findByCompany(Company company, Pageable pageable);
    Optional<User> findFirstByCompanyAndRole(Company company, Role role);
    long countByCompanyAndRolesContaining(Company company, Role role);
}
