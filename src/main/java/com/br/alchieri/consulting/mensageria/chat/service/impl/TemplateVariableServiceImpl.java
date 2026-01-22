package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.response.VariableDictionaryResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.VariableDictionaryResponse.Variable;
import com.br.alchieri.consulting.mensageria.chat.dto.response.VariableDictionaryResponse.VariableGroup;
import com.br.alchieri.consulting.mensageria.chat.service.TemplateVariableService;

@Service
public class TemplateVariableServiceImpl implements TemplateVariableService {

    @Override
    public VariableDictionaryResponse getVariableDictionary() {
        List<VariableGroup> groups = new ArrayList<>();
        
        // --- Grupo de Variáveis do Contato ---
        List<Variable> contactVariables = List.of(
            new Variable("Nome do Contato", "name", "contact.name", "João da Silva"),
            new Variable("Número de Telefone", "phoneNumber", "contact.phoneNumber", "5511999998888"),
            new Variable("Email do Contato", "email", "contact.email", "joao.silva@exemplo.com"),
            new Variable("Data de Nascimento", "dateOfBirth", "contact.dateOfBirth", "1990-05-15"),
            new Variable("Empresa do Contato", "companyName", "contact.companyName", "ACME Corp"),
            new Variable("Cargo do Contato", "jobTitle", "contact.jobTitle", "Gerente")
            // Para campos customizados, você pode ter uma variável genérica ou listar as mais comuns
            // new Variable("Campo Customizado (ex: cidade)", "customFields.cidade", "contact.customFields.cidade", "São Paulo")
        );
        groups.add(new VariableGroup("Dados do Contato", "contact", contactVariables));

        // --- Grupo de Variáveis da Empresa (que está enviando) ---
        List<Variable> companyVariables = List.of(
            new Variable("Nome da Empresa", "name", "company.name", "Alchieri Consulting"),
            new Variable("Email de Contato da Empresa", "contactEmail", "company.contactEmail", "contato@alchieri.com"),
            new Variable("Telefone de Contato da Empresa", "contactPhoneNumber", "company.contactPhoneNumber", "+551155554444")
        );
        groups.add(new VariableGroup("Dados da Empresa", "company", companyVariables));

        // --- Grupo de Variáveis do Usuário (que está enviando) ---
        List<Variable> userVariables = List.of(
            new Variable("Nome Completo do Remetente", "fullName", "user.fullName", "Carlos Andrade"),
            new Variable("Email do Remetente", "email", "user.email", "carlos.andrade@alchieri.com")
        );
        groups.add(new VariableGroup("Dados do Remetente (Usuário)", "user", userVariables));

        return new VariableDictionaryResponse(groups);
    }
}
