package com.br.alchieri.consulting.mensageria.chat.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplatePushResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplateSyncResponse;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.User;

import reactor.core.publisher.Mono;

/**
 * Serviço responsável por interagir com a WhatsApp Business Management API (WABMA)
 * para operações administrativas como gerenciamento de templates.
 * As operações são realizadas no contexto de um usuário autenticado e sua empresa associada.
 */
public interface WhatsAppBusinessApiService {

    /**
     * Submete um novo modelo de mensagem para aprovação na plataforma da Meta e cria um registro
     * correspondente no banco de dados local.
     *
     * @param request DTO com a definição completa do template a ser criado.
     * @param creator O usuário autenticado que está realizando a operação.
     * @return Um Mono contendo a entidade ClientTemplate salva com o status inicial de submissão.
     */
    Mono<ClientTemplate> createTemplate(CreateTemplateRequest request, User creator);

    /**
     * Lista os templates de mensagem a partir do banco de dados local.
     * Para usuários BSP_ADMIN, permite listar todos os templates ou filtrar por uma empresa.
     * Para outros usuários, lista apenas os templates da sua própria empresa.
     *
     * @param user      O usuário autenticado realizando a busca.
     * @param companyId Opcional. ID da empresa para filtrar (apenas para BSP_ADMIN).
     * @param pageable  Informações de paginação.
     * @return Uma página de entidades ClientTemplate.
     */
    Page<ClientTemplate> listTemplatesForUser(User user, Optional<Long> companyId, Pageable pageable);

    /**
     * Busca os detalhes completos de um template específico (nome e idioma) a partir do banco de dados local
     * para a empresa do usuário autenticado.
     *
     * @param templateName Nome do template.
     * @param language     Código do idioma.
     * @param user         O usuário autenticado.
     * @return Um Mono com a entidade ClientTemplate se encontrada, ou Mono.empty() se não.
     */
    Mono<ClientTemplate> getTemplateDetails(String templateName, String language, User user);

    /**
     * Exclui um modelo de mensagem da plataforma da Meta e do banco de dados local.
     *
     * @param templateName Nome do template a ser excluído.
     * @param language     Código do idioma.
     * @param user         O usuário autenticado realizando a operação.
     * @return Um Mono com a resposta da API, indicando sucesso ou falha na operação.
     */
    Mono<ApiResponse> deleteTemplate(String templateName, String language, User user);

    /**
     * Sincroniza os templates da conta Meta da empresa do usuário com o banco de dados local.
     * Busca todos os templates na Meta e cria/atualiza os registros na tabela ClientTemplate.
     *
     * @param user O usuário autenticado cuja empresa será sincronizada.
     * @return Um Mono com o resumo da operação de sincronização.
     */
    Mono<TemplateSyncResponse> syncTemplatesFromMeta(User user);

    /**
     * Envia templates que existem no banco de dados local mas não na conta da Meta para uma empresa alvo.
     * Operação geralmente acionada por um administrador BSP.
     *
     * @param companyId O ID da empresa alvo para a qual os templates serão enviados.
     * @param user      O usuário (admin) que está acionando a operação.
     * @return Um Mono com o resumo da operação de envio.
     */
    Mono<TemplatePushResponse> pushLocalTemplatesToMeta(Long companyId, User user);

    /**
     * Busca todos os templates com um nome específico para a empresa do usuário autenticado.
     * Um nome de template pode ter múltiplas versões, uma para cada idioma.
     *
     * @param templateName Nome do template.
     * @param user O usuário autenticado.
     * @return Uma lista de entidades ClientTemplate correspondentes ao nome (uma por idioma).
     */
    List<ClientTemplate> findTemplatesByNameForUser(String templateName, User user);
}
