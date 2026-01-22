package com.br.alchieri.consulting.mensageria.chat.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FlowCategory {

    SIGN_UP,
    SIGN_IN,
    APPOINTMENT_BOOKING,
    LEAD_GENERATION,
    CONTACT_US,
    CUSTOMER_SUPPORT,
    SURVEY,
    SHOPPING_CART,
    ORDER_MANAGEMENT,
    MENU,
    SEARCH,
    RESERVE,
    OTHER;

    /**
     * A anotação @JsonValue garante que, ao serializar/desserializar com Jackson,
     * o valor usado será a string exata do enum (ex: "SIGN_UP"),
     * o que é exatamente o que a API da Meta espera.
     */
    @JsonValue
    public String getValue() {
        return this.name();
    }
}
