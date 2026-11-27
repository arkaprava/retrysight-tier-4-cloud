/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.graphql.PlaygroundEvalResult;
import com.agentretry.manager.repository.EvalDefinitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class EvalPlaygroundService {
    private final EvalDefinitionRepository definitionRepository;
    private final EvalRunnerService runnerService;

    public EvalPlaygroundService(EvalDefinitionRepository definitionRepository, EvalRunnerService runnerService) {
        this.definitionRepository = definitionRepository;
        this.runnerService = runnerService;
    }

    @Transactional
    public PlaygroundEvalResult run(UUID taskId, UUID definitionId, String promptTemplate) {
        EvalDefinitionEntity definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval definition not found"));
        String prompt = promptTemplate != null && !promptTemplate.isBlank()
                ? promptTemplate
                : definition.getPromptTemplate();
        EvalRunEntity run = runnerService.runSingleDefinition(taskId, definitionId, EvalTrigger.MANUAL, prompt);
        return new PlaygroundEvalResult(
                taskId,
                definitionId,
                run.getLabel() != null ? run.getLabel().name() : run.getStatus().name(),
                run.getScore(),
                run.getExplanation(),
                prompt
        );
    }
}
