package com.br.alchieri.consulting.mensageria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable // Indica que esta classe pode ser embutida em outras entidades
@Data
@NoArgsConstructor
public class Address {

    @Column(length = 200)
    @Size(max = 200)
    private String street; // Logradouro

    @Column(length = 20)
    @Size(max = 20)
    private String number; // Número

    @Column(length = 100)
    @Size(max = 100)
    private String complement; // Complemento (apt, bloco, etc.)

    @Column(length = 100)
    @Size(max = 100)
    private String neighborhood; // Bairro

    @Column(length = 100)
    @Size(max = 100)
    private String city; // Cidade

    @Column(length = 50) // Ou 2 para sigla do estado
    @Size(max = 50)
    private String state; // Estado/Província

    @Column(length = 20)
    @Size(max = 20)
    private String postalCode; // CEP

    @Column(length = 100)
    @Size(max = 100)
    private String country; // País (pode ter um valor padrão)
}
