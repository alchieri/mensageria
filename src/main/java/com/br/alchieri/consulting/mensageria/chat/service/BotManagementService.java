package com.br.alchieri.consulting.mensageria.chat.service;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.dto.request.BotStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateBotWithStructureRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.UpdateBotRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotResponseDTO;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BotStepDTO;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface BotManagementService {

    List<BotResponseDTO> listBots(Company company);

    BotResponseDTO createBotWithStructure(Company company, CreateBotWithStructureRequest request);

    BotResponseDTO updateBot(Long botId, UpdateBotRequest request, Company company);

    BotResponseDTO getBot(Long botId, Company company);

    void deleteBot(Long botId, Company company);

    BotStepDTO addStep(Long botId, BotStepDTO stepDTO, Company company);

    BotStepDTO updateStep(Long stepId, BotStepDTO dto);

    void linkSteps(Long originStepId, Long targetStepId, String keyword, String label);

    BotStepDTO getStepDetails(Long stepId);

    void saveBotStructure(Long botId, BotStructureRequest request, Company company);
}
