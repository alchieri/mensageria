package com.br.alchieri.consulting.mensageria.chat.dto.request;

import com.br.alchieri.consulting.mensageria.model.Address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "AddressInput", description = "Detalhes do endereço para entrada.")
public class AddressRequestDTO {

    @Size(max = 200) @Schema(example = "Rua Principal")
    private String street;

    @Size(max = 20) @Schema(example = "123 A")
    private String number;

    @Size(max = 100) @Schema(example = "Sala 10")
    private String complement;

    @Size(max = 100) @Schema(example = "Centro")
    private String neighborhood;

    @NotBlank(message = "Cidade é obrigatória") @Size(max = 100)
    @Schema(example = "São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @NotBlank(message = "Estado é obrigatório") @Size(max = 50)
    @Schema(example = "SP", requiredMode = Schema.RequiredMode.REQUIRED)
    private String state;

    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "^[0-9]{5}-?[0-9]{3}$", message="CEP inválido")
    @Schema(example = "01000-000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postalCode;

    @Size(max = 100) @Schema(example = "Brasil", defaultValue = "Brasil")
    private String country = "Brasil";

    public static AddressRequestDTO fromEntity(Address addressEntity) {
        if (addressEntity == null) return null;
        return AddressRequestDTO.builder()
                .street(addressEntity.getStreet())
                .number(addressEntity.getNumber())
                .complement(addressEntity.getComplement())
                .neighborhood(addressEntity.getNeighborhood())
                .city(addressEntity.getCity())
                .state(addressEntity.getState())
                .postalCode(addressEntity.getPostalCode())
                .country(addressEntity.getCountry())
                .build();
    }

    public Address toEntity() {
        Address addressEntity = new Address();
        addressEntity.setStreet(this.street);
        addressEntity.setNumber(this.number);
        addressEntity.setComplement(this.complement);
        addressEntity.setNeighborhood(this.neighborhood);
        addressEntity.setCity(this.city);
        addressEntity.setState(this.state);
        addressEntity.setPostalCode(this.postalCode);
        addressEntity.setCountry(this.country);
        return addressEntity;
    }
}
