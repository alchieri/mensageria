package com.br.alchieri.consulting.mensageria.validation;

import org.springframework.util.StringUtils;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ContactTargetValidator implements ConstraintValidator<ContactTarget, SendTemplateMessageRequest> {

    @Override
    public boolean isValid(SendTemplateMessageRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Deixa outras anotações cuidarem do nulo
        }

        boolean isContactIdPresent = value.getContactId() != null && value.getContactId() > 0;
        boolean isToPresent = StringUtils.hasText(value.getTo());

        // Regra: Exclusivamente um dos dois deve estar presente
        return isContactIdPresent ^ isToPresent; // XOR (OU exclusivo)
    }
}
