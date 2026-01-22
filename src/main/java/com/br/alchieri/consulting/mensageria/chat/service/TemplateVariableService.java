package com.br.alchieri.consulting.mensageria.chat.service;

import com.br.alchieri.consulting.mensageria.chat.dto.response.VariableDictionaryResponse;

public interface TemplateVariableService {

    /**
     * Retorna a estrutura completa do dicionário de variáveis dinâmicas.
     * @return O dicionário de variáveis.
     */
    VariableDictionaryResponse getVariableDictionary();
}
