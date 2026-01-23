package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.Tag;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    // Encontra uma tag pelo nome e pela empresa dona
    Optional<Tag> findByCompanyAndNameIgnoreCase(Company company, String name);

    // Encontra um conjunto de tags pelos seus nomes e pela empresa dona
    Set<Tag> findByCompanyAndNameIn(Company company, List<String> names);
}
