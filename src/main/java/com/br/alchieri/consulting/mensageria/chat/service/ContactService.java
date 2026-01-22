package com.br.alchieri.consulting.mensageria.chat.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.br.alchieri.consulting.mensageria.chat.dto.response.CsvImportResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.dto.request.ContactRequest;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface ContactService {

    Page<Contact> getContactsByCompany(Company company, Pageable pageable);
    Optional<Contact> getContactByIdAndCompany(Long contactId, Company company);
    Contact createContact(ContactRequest request, Company company);
    Contact updateContact(Long contactId, ContactRequest request, Company company);
    void deleteContact(Long contactId, Company company);
    CsvImportResponse importContactsFromCsv(MultipartFile file, Company company);
}
