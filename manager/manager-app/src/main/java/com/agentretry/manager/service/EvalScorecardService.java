/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.EvalScorecardEntity;
import com.agentretry.manager.graphql.EvalScorecard;
import com.agentretry.manager.repository.EvalScorecardRepository;
import com.agentretry.manager.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EvalScorecardService {
    private final EvalScorecardRepository repository;
    private final TaskRepository taskRepository;

    public EvalScorecardService(EvalScorecardRepository repository, TaskRepository taskRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<EvalScorecard> list(UUID taskId) {
        List<EvalScorecardEntity> entities = taskId != null
                ? repository.findByTaskIdOrderByCreatedAtDesc(taskId)
                : repository.findAllByOrderByCreatedAtDesc();
        return entities.stream().map(this::toGraphql).toList();
    }

    @Transactional
    public EvalScorecard create(UUID taskId, double score, String notes, String createdBy) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (score < 0 || score > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be between 0 and 100");
        }
        EvalScorecardEntity entity = new EvalScorecardEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setScore(score);
        entity.setNotes(notes);
        entity.setCreatedBy(createdBy != null ? createdBy : "unknown");
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
        return toGraphql(entity);
    }

    private EvalScorecard toGraphql(EvalScorecardEntity entity) {
        return new EvalScorecard(
                entity.getId(),
                entity.getTaskId(),
                entity.getScore(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}
