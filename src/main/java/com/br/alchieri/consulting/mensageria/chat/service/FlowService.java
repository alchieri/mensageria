package com.br.alchieri.consulting.mensageria.chat.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowJsonUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowMetricResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowSyncResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowMetricName;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MetricGranularity;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.Company;

import reactor.core.publisher.Mono;

public interface FlowService {

    /** Lista os Flows da empresa a partir do banco de dados local. */
    Page<Flow> listFlowsByCompany(Company company, Pageable pageable);

    /** Busca um Flow específico pelo ID local e pela empresa. */
    Optional<Flow> getFlowByIdAndCompany(Long flowId, Company company);

    /**
     * Cria um novo Flow na plataforma da Meta e o salva localmente.
     * @param request DTO com nome, categorias e a definição JSON do Flow.
     * @param company A empresa proprietária.
     * @param publish Se deve tentar publicar o Flow imediatamente após a criação.
     * @return O Flow criado.
     */
    Mono<Flow> createFlow(FlowRequest request, Company company, boolean publish);

    /**
     * Atualiza os metadados (nome, categorias, endpoint_uri) de um Flow existente.
     * @param flowId O ID do Flow (do seu sistema) a ser atualizado.
     * @param request DTO com os novos metadados.
     * @param company A empresa proprietária.
     * @return O Flow atualizado.
     */
    Mono<Flow> updateFlowMetadata(Long flowId, FlowUpdateRequest request, Company company);

    /**
     * Atualiza a definição JSON de um Flow existente na Meta.
     * @param flowId O ID do Flow (do seu sistema) a ser atualizado.
     * @param request DTO com a nova definição JSON.
     * @param company A empresa proprietária.
     * @return O Flow atualizado (que voltará ao status DRAFT na Meta).
     */
    Mono<Flow> updateFlowJson(Long flowId, FlowJsonUpdateRequest request, Company company);

    /**
     * Publica um Flow que está em status DRAFT na Meta.
     * @param flowId O ID do Flow (do seu sistema) a ser publicado.
     * @param company A empresa proprietária.
     * @return O Flow atualizado com o status PUBLISHED.
     */
    Mono<Flow> publishFlow(Long flowId, Company company);

    /** Exclui um Flow da Meta e do banco local. */
    Mono<ApiResponse> deleteFlow(Long flowId, Company company);

    /** Desativa (deprecates) um Flow na Meta. */
    Mono<Flow> deprecateFlow(Long flowId, Company company);

    /** Busca e atualiza o status de um Flow local com base nos dados da Meta. */
    Mono<Flow> fetchAndSyncFlowStatus(Long flowId, Company company);

    /** Sincroniza todos os Flows de uma WABA com o banco local. */
    Mono<FlowSyncResponse> syncFlowsFromMeta(Company company);

    /**
     * Busca métricas de um Flow específico da API da Meta.
     *
     * @param flowId O ID do Flow (do seu sistema).
     * @param company A empresa proprietária.
     * @param metricName O nome da métrica a ser consultada.
     * @param granularity A granularidade do tempo (DAY, HOUR, LIFETIME).
     * @param since Data de início (opcional).
     * @param until Data de fim (opcional).
     * @return Um Mono com a resposta da API de Métricas.
     */
    Mono<FlowMetricResponse> getFlowMetrics(Long flowId, Company company,
                                            FlowMetricName metricName, MetricGranularity granularity,
                                            LocalDate since, LocalDate until);
}
