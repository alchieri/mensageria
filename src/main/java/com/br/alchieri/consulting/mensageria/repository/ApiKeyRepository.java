package com.br.alchieri.consulting.mensageria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.ApiKey;
import com.br.alchieri.consulting.mensageria.model.User;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
 
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserAndActiveTrue(User user);
    List<ApiKey> findByUser(User user);
}
