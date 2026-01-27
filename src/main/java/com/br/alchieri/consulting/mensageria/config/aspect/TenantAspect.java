package com.br.alchieri.consulting.mensageria.config.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.br.alchieri.consulting.mensageria.model.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Aspect
@Component
public class TenantAspect {

    @PersistenceContext
    private EntityManager entityManager;

    // Executa antes de qualquer método em classes no pacote 'service'
    @Before("execution(* com.br.alchieri.consulting.mensageria..service..*(..))")
    public void enableTenantFilter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User) {
            User user = (User) auth.getPrincipal();
            Long companyId = user.getCompany().getId();

            // Desembrulha a sessão do Hibernate e ativa o filtro
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("companyId", companyId);
        }
    }
}
