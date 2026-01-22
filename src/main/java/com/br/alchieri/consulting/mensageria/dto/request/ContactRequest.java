package com.br.alchieri.consulting.mensageria.dto.request;

import java.time.LocalDate;
import java.util.Set;

import com.br.alchieri.consulting.mensageria.chat.dto.request.AddressRequestDTO;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.enums.ContactStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.LeadSource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Dados para criar ou atualizar um contato.")
public class ContactRequest {

    @NotBlank(message = "Nome é obrigatório.")
    @Schema(example = "João da Silva", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Número de telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{12,13}$", message = "Número de telefone deve conter apenas números, incluindo código do país e área (ex: 5511999998888).")
    @Schema(example = "5511999998888", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;

    @Email(message = "Formato de email inválido.")
    @Schema(example = "joao.silva@email.com")
    private String email;

    @Past(message = "Data de nascimento deve ser no passado.")
    @Schema(description = "Formato: YYYY-MM-DD", example = "1990-05-15")
    private LocalDate dateOfBirth;

    @Schema(allowableValues = {"MASCULINO", "FEMININO", "OUTRO", "PREFIRO_NAO_INFORMAR"})
    private Contact.Gender gender;

    // --- Informações Profissionais ---
    @Schema(example = "ACME Corp")
    private String companyName;

    @Schema(example = "Gerente de Vendas")
    private String jobTitle;

    @Schema(example = "Vendas")
    private String department;

    // --- Endereço ---
    @Valid // Para validar os campos dentro de AddressRequestDTO
    @Schema(description = "Endereço do contato.")
    private AddressRequestDTO address; // Reutilizando DTO

    // --- Preferências e Status ---
    @Schema(description = "Status do contato.", defaultValue = "ACTIVE")
    private ContactStatus status = ContactStatus.ACTIVE;

    @Schema(description = "Idioma preferido do contato (ex: pt-BR).", example = "pt-BR")
    private String preferredLanguage;

    @Schema(description = "Fuso horário do contato (ex: America/Sao_Paulo).", example = "America/Sao_Paulo")
    private String timeZone;

    @Schema(description = "Indica se o contato é VIP.", defaultValue = "false")
    private Boolean isVip = false;

    @Schema(description = "Permite envio de mensagens de marketing.", defaultValue = "true")
    private Boolean allowMarketingMessages = true;

    @Schema(description = "Permite envio de notificações transacionais.", defaultValue = "true")
    private Boolean allowNotifications = true;

    // --- Dados de CRM/Marketing ---
    @Schema(description = "Fonte/Origem do lead.")
    private LeadSource leadSource;

    @Schema(description = "Pontuação do lead (ex: de 0 a 100).", example = "75")
    private Integer leadScore;

    @Schema(description = "Observações gerais sobre o contato.")
    private String notes;

    // --- Tags ---
    @Schema(description = "Lista de tags a serem associadas ao contato. Tags inexistentes serão criadas.",
            example = "[\"Cliente VIP\", \"Lead Qualificado\"]")
    private Set<String> tags;
}
