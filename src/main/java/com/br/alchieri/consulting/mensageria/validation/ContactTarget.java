package com.br.alchieri.consulting.mensageria.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Constraint(validatedBy = ContactTargetValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ContactTarget {

    String message() default "É obrigatório fornecer 'contactId' OU 'to', mas não ambos.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
