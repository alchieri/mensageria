@FilterDef(
    name = "tenantFilter", 
    parameters = @ParamDef(name = "companyId", type = Long.class),
    defaultCondition = "company_id = :companyId" // Opcional: define a condição padrão se a maioria das tabelas usar a mesma coluna
)
package com.br.alchieri.consulting.mensageria;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;