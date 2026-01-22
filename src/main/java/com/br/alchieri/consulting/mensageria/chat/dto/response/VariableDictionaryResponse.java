package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estrutura do dicionário de variáveis dinâmicas disponíveis para templates.")
public class VariableDictionaryResponse {

    @Schema(description = "Lista de grupos de variáveis (ex: Contato, Empresa).")
    private List<VariableGroup> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Um grupo de variáveis relacionadas.")
    public static class VariableGroup {
        @Schema(description = "Nome do grupo.", example = "Dados do Contato")
        private String groupName;

        @Schema(description = "Prefixo usado no 'sourceField' para este grupo.", example = "contact")
        private String prefix;

        @Schema(description = "Lista de variáveis dentro deste grupo.")
        private List<Variable> variables;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Descrição de uma única variável dinâmica.")
    public static class Variable {
        @Schema(description = "Nome amigável da variável, para ser exibido na UI.", example = "Nome Completo")
        private String displayName;

        @Schema(description = "A chave a ser usada no campo 'sourceField' do mapeamento.", example = "name")
        private String fieldName;

        @Schema(description = "A string completa para o 'sourceField'.", example = "contact.name")
        private String fullSourcePath;

        @Schema(description = "Exemplo de valor que esta variável poderia ter.", example = "João da Silva")
        private String exampleValue;
    }
}
