package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.request.BotOptionStructureDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.request.BotStepStructureDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.request.BotStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotWithStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.UpdateBotRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotOptionDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotResponseDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotStepDTO;
import com.br.alchieri.consulting.mensageria.chat.model.Bot;
import com.br.alchieri.consulting.mensageria.chat.model.BotOption;
import com.br.alchieri.consulting.mensageria.chat.model.BotStep;
import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;
import com.br.alchieri.consulting.mensageria.chat.repository.BotOptionRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.BotRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.BotStepRepository;
import com.br.alchieri.consulting.mensageria.chat.service.BotManagementService;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotManagementServiceImpl implements BotManagementService {

    private final BotRepository botRepository;
    private final BotStepRepository botStepRepository;
    private final BotOptionRepository botOptionRepository;

    @Override
    public List<BotResponseDTO> listBots(Company company) {
        return botRepository.findByCompany(company).stream()
                .map(this::toBotDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public BotResponseDTO createBotWithStructure(Company company, CreateBotWithStructureRequest request) {
        // 1. Criar e Salvar o Cabeçalho do Bot (Metadados)
        Bot bot = new Bot();
        bot.setCompany(company);
        bot.setName(request.getName());
        bot.setTriggerType(request.getTriggerType());
        bot.setStartTime(request.getStartTime());
        bot.setEndTime(request.getEndTime());
        bot.setActiveDays(request.getActiveDays());
        bot.setActive(true);

        // Salvamos para obter o ID (necessário para os passos referenciarem)
        bot = botRepository.save(bot);

        // 2. Processar a Estrutura (Steps)
        // Verificamos se há passos para salvar
        if (request.getSteps() != null && !request.getSteps().isEmpty()) {
            
            // Convertemos para o DTO que o método de estrutura espera
            BotStructureRequest structureRequest = new BotStructureRequest();
            structureRequest.setRootStepTempId(request.getRootStepTempId());
            structureRequest.setSteps(request.getSteps());

            // REUTILIZAÇÃO: Chamamos a lógica de grafo existente
            saveBotStructure(bot.getId(), structureRequest, company);
            
            // Recarrega o bot para garantir que o rootStep esteja atualizado no objeto
            bot = botRepository.findById(bot.getId()).orElseThrow();
        }

        return toBotDTO(bot);
    }

    @Transactional
    @Override
    public BotResponseDTO updateBot(Long botId, UpdateBotRequest request, Company company) {
        // Reutiliza o método findBot existente para garantir segurança por empresa
        Bot bot = findBot(botId, company);

        bot.setName(request.getName());
        
        if (request.getTriggerType() != null) {
            bot.setTriggerType(request.getTriggerType());
        }
        
        bot.setStartTime(request.getStartTime());
        bot.setEndTime(request.getEndTime());
        bot.setActiveDays(request.getActiveDays());
        
        if (request.getIsActive() != null) {
            bot.setActive(request.getIsActive());
        }

        Bot savedBot = botRepository.save(bot);
        return toBotDTO(savedBot);
    }

    @Override
    public BotResponseDTO getBot(Long botId, Company company) {
        Bot bot = findBot(botId, company);
        return toBotDTO(bot);
    }
    
    @Transactional
    @Override
    public void deleteBot(Long botId, Company company) {
        Bot bot = findBot(botId, company);
        botRepository.delete(bot);
    }

    // --- GERENCIAMENTO DE PASSOS (STEPS) ---

    @Transactional
    @Override
    public BotStepDTO addStep(Long botId, BotStepDTO stepDTO, Company company) {
        // Valida se o bot pertence à empresa
        findBot(botId, company);

        BotStep step = new BotStep();
        step.setTitle(stepDTO.getTitle());
        step.setStepType(stepDTO.getStepType());
        step.setContent(stepDTO.getContent());
        step.setMetadata(stepDTO.getMetadata());
        
        BotStep savedStep = botStepRepository.save(step);
        return toStepDTO(savedStep);
    }
    
    @Transactional
    @Override
    public BotStepDTO updateStep(Long stepId, BotStepDTO dto) {
        BotStep step = botStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Passo não encontrado"));
        
        step.setTitle(dto.getTitle());
        step.setContent(dto.getContent());
        step.setMetadata(dto.getMetadata());
        step.setStepType(dto.getStepType());
        
        return toStepDTO(botStepRepository.save(step));
    }

    // --- GERENCIAMENTO DE OPÇÕES (LINKS) ---

    @Transactional
    @Override
    public void linkSteps(Long originStepId, Long targetStepId, String keyword, String label) {
        
        BotStep origin = botStepRepository.findById(originStepId).orElseThrow();
        BotStep target = botStepRepository.findById(targetStepId).orElseThrow();

        BotOption option = new BotOption();
        option.setStep(origin);
        option.setTargetStep(target);
        option.setKeyword(keyword);
        option.setLabel(label);
        option.setSequence(origin.getOptions().size() + 1);

        botOptionRepository.save(option);
    }
    
    @Override
    public BotStepDTO getStepDetails(Long stepId) {
        BotStep step = botStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Passo não encontrado"));
        return toStepDTO(step);
    }

    @Transactional
    @Override
    public void saveBotStructure(Long botId, BotStructureRequest request, Company company) {
        Bot bot = findBot(botId, company);

        // 1. Carregar passos existentes para identificar atualizações vs criações
        List<BotStep> existingSteps = botStepRepository.findByBotId(botId);
        Map<Long, BotStep> existingMap = existingSteps.stream()
                .collect(Collectors.toMap(BotStep::getId, Function.identity()));

        // Mapa para resolver conexões: TempID (Front) -> Entidade Persistida
        Map<String, BotStep> tempIdToEntityMap = new HashMap<>();

        // 2. Primeira Passada: Salvar/Atualizar os Passos (sem opções/conexões ainda)
        List<BotStep> stepsToKeep = new ArrayList<>();

        for (BotStepStructureDTO stepDto : request.getSteps()) {
            BotStep stepEntity;

            // É atualização?
            if (stepDto.getId() != null && existingMap.containsKey(stepDto.getId())) {
                stepEntity = existingMap.get(stepDto.getId());
            } else {
                // É criação
                stepEntity = new BotStep();
                stepEntity.setBot(bot);
            }

            // Atualiza dados básicos
            stepEntity.setTitle(stepDto.getTitle());
            stepEntity.setStepType(stepDto.getStepType());
            stepEntity.setContent(stepDto.getContent());
            stepEntity.setMetadata(stepDto.getMetadata());

            // Salva para garantir ID (se for novo)
            stepEntity = botStepRepository.save(stepEntity);
            
            stepsToKeep.add(stepEntity);
            
            // Registra no mapa de TempIDs
            if (stepDto.getTempId() != null) {
                tempIdToEntityMap.put(stepDto.getTempId(), stepEntity);
            }
            // Se tiver ID real, mapeia também (para facilitar lookup reverso se precisar)
            tempIdToEntityMap.put(String.valueOf(stepEntity.getId()), stepEntity);
        }

        // 3. Segunda Passada: Construir as Opções e Conexões (Graph Links)
        for (BotStepStructureDTO stepDto : request.getSteps()) {
            BotStep parentStep = tempIdToEntityMap.get(stepDto.getTempId());
            if (parentStep == null && stepDto.getId() != null) {
                parentStep = tempIdToEntityMap.get(String.valueOf(stepDto.getId()));
            }

            if (parentStep == null) continue; // Should not happen

            // Limpa opções antigas e recria (estratégia mais segura para grafos complexos)
            // Se quiser preservar IDs de opções, a lógica seria mais complexa (merge).
            // Aqui assumimos replace das opções para simplificar.
            if (parentStep.getOptions() == null) {
                parentStep.setOptions(new ArrayList<>());
            }
            parentStep.getOptions().clear();

            if (stepDto.getOptions() != null) {
                for (BotOptionStructureDTO optDto : stepDto.getOptions()) {
                    BotOption option = new BotOption();
                    option.setStep(parentStep);
                    option.setKeyword(optDto.getKeyword());
                    option.setLabel(optDto.getLabel());
                    option.setSequence(optDto.getSequence());
                    option.setHandoff(optDto.isHandoff());

                    // Resolver Destino (Target)
                    BotStep targetStep = null;
                    if (optDto.getTargetStepTempId() != null) {
                        targetStep = tempIdToEntityMap.get(optDto.getTargetStepTempId());
                    } else if (optDto.getTargetStepId() != null) {
                        // Tenta achar no mapa atual ou busca no banco (se for link para outro bot/externo)
                        // Assumindo link interno:
                        targetStep = tempIdToEntityMap.get(String.valueOf(optDto.getTargetStepId()));
                    }

                    option.setTargetStep(targetStep);
                    parentStep.getOptions().add(option);
                }
            }
            botStepRepository.save(parentStep); // Salva com as opções
        }

        // 4. Remover Passos Órfãos (que estavam no banco mas não vieram no JSON)
        existingSteps.removeAll(stepsToKeep);
        if (!existingSteps.isEmpty()) {
            // Cuidado: validar se o root não está sendo deletado sem querer
            botStepRepository.deleteAll(existingSteps);
        }

        // 5. Atualizar Root do Bot
        if (request.getRootStepTempId() != null) {
            BotStep newRoot = tempIdToEntityMap.get(request.getRootStepTempId());
            if (newRoot != null) {
                bot.setRootStep(newRoot);
                botRepository.save(bot);
            }
        }
    }

    // --- MAPPERS ---

    private Bot findBot(Long id, Company company) {
        return botRepository.findById(id)
                .filter(b -> b.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Bot não encontrado"));
    }

    private BotResponseDTO toBotDTO(Bot bot) {
        BotResponseDTO dto = new BotResponseDTO();
        dto.setId(bot.getId());
        dto.setName(bot.getName());
        dto.setActive(bot.isActive());
        dto.setTriggerType(bot.getTriggerType());
        dto.setStartTime(bot.getStartTime());
        dto.setEndTime(bot.getEndTime());
        dto.setActiveDays(bot.getActiveDays());
        if (bot.getRootStep() != null) {
            dto.setRootStepId(bot.getRootStep().getId());
        }
        return dto;
    }

    private BotStepDTO toStepDTO(BotStep step) {
        BotStepDTO dto = new BotStepDTO();
        dto.setId(step.getId());
        dto.setTitle(step.getTitle());
        dto.setStepType(step.getStepType());
        dto.setContent(step.getContent());
        dto.setMetadata(step.getMetadata());

        if (step.getOptions() != null) {
            List<BotOptionDTO> options = step.getOptions().stream().map(opt -> {
                BotOptionDTO optDto = new BotOptionDTO();
                optDto.setId(opt.getId());
                optDto.setKeyword(opt.getKeyword());
                optDto.setLabel(opt.getLabel());
                optDto.setSequence(opt.getSequence());
                optDto.setHandoff(opt.isHandoff());
                if (opt.getTargetStep() != null) {
                    optDto.setTargetStepId(opt.getTargetStep().getId());
                }
                return optDto;
            }).collect(Collectors.toList());
            dto.setOptions(options);
        }
        return dto;
    }
}
