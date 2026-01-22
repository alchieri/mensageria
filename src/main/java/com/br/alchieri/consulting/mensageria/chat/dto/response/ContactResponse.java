package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.br.alchieri.consulting.mensageria.chat.dto.request.AddressRequestDTO;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Tag;
import com.br.alchieri.consulting.mensageria.chat.model.enums.ContactStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.LeadSource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Informações de um contato.")
public class ContactResponse {

    private Long id;
    private String name;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private Contact.Gender gender;
    private String companyName;
    private String jobTitle;
    private String department;
    private AddressRequestDTO address;
    private ContactStatus status;
    private String preferredLanguage;
    private String timeZone;
    private Boolean isVip;
    private Boolean allowMarketingMessages;
    private Boolean allowNotifications;
    private LeadSource leadSource;
    private Integer leadScore;
    private String notes;
    private Set<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactResponse fromEntity(Contact contact) {
        if (contact == null) return null;
        return ContactResponse.builder()
                .id(contact.getId())
                .name(contact.getName())
                .phoneNumber(contact.getPhoneNumber())
                .email(contact.getEmail())
                .dateOfBirth(contact.getDateOfBirth())
                .gender(contact.getGender())
                .companyName(contact.getCompanyName())
                .jobTitle(contact.getJobTitle())
                .department(contact.getDepartment())
                .address(AddressRequestDTO.fromEntity(contact.getAddress()))
                .status(contact.getStatus())
                .preferredLanguage(contact.getPreferredLanguage())
                .timeZone(contact.getTimeZone())
                .isVip(contact.isVip())
                .allowMarketingMessages(contact.isAllowMarketingMessages())
                .allowNotifications(contact.isAllowNotifications())
                .leadSource(contact.getLeadSource())
                .leadScore(contact.getLeadScore())
                .notes(contact.getNotes())
                .tags(contact.getTags().stream().map(Tag::getName).collect(Collectors.toSet()))
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }
}
