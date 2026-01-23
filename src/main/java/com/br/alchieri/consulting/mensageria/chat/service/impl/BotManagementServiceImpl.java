package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotRequest;
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
    public BotResponseDTO createBot(Company company, CreateBotRequest request) {
        Bot bot = new Bot();
        bot.setCompany(company);
        bot.setName(request.getName());
        bot.setTriggerType(request.getTriggerType());
        bot.setStartTime(request.getStartTime());
        bot.setEndTime(request.getEndTime());
        bot.setActiveDays(request.getActiveDays());
        bot.setActive(true);

        // Cria automaticamente um Root Step para o bot não nascer quebrado
        BotStep rootStep = new BotStep();
        rootStep.setTitle("Início");
        rootStep.setStepType(BotStepType.TEXT);
        rootStep.setContent("Olá! Eu sou o assistente virtual.");
        botStepRepository.save(rootStep);

        bot.setRootStep(rootStep);
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
        option.setOriginStep(origin);
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
