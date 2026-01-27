package com.br.alchieri.consulting.mensageria.service;

import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;

public interface CartService {

    void addItemToSession(UserSession session, String sku, int quantity);

    void clearCart(UserSession session);

    Order checkout(UserSession session, Contact contact, WhatsAppPhoneNumber channel);
}
