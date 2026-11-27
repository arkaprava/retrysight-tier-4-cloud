/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.EvalDatasetEntity;
import com.agentretry.manager.domain.EvalDatasetItemEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.graphql.EvalDataset;
import com.agentretry.manager.repository.EvalDatasetItemRepository;
import com.agentretry.manager.repository.EvalDatasetRepository;
import com.agentretry.manager.repository.EvalRunRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TaskSpecifications;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EvalDatasetService {
    private final EvalDatasetRepository datasetRepository;
    private final EvalDatasetItemRepository itemRepository;
    private final TaskRepository taskRepository;
    private final EvalRunRepository evalRunRepository;

    public EvalDatasetService(
            EvalDatasetRepository datasetRepository,
            EvalDatasetItemRepository itemRepository,
            TaskRepository taskRepository,
            EvalRunRepository evalRunRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.itemRepository = itemRepository;
        this.taskRepository = taskRepository;
        this.evalRunRepository = evalRunRepository;
    }

    @Transactional(readOnly = true)
    public List<EvalDataset> list() {
        return datasetRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvalDataset get(UUID id) {
        return datasetRepository.findById(id).map(this::toGraphql)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }

    @Transactional(readOnly = true)
    public List<UUID> taskIds(UUID datasetId) {
        return itemRepository.findByDatasetIdOrderByAddedAtDesc(datasetId).stream()
                .map(EvalDatasetItemEntity::getTaskId)
                .toList();
    }

    @Transactional
    public EvalDataset create(String name, String description, EvalDatasetSource source, String createdBy) {
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name != null ? name.trim() : "Dataset");
        entity.setDescription(description);
        entity.setSource(source != null ? source : EvalDatasetSource.MANUAL);
        entity.setCreatedBy(createdBy);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toGraphql(datasetRepository.save(entity));
    }

    @Transactional
    public EvalDataset addTask(UUID datasetId, UUID taskId) {
        requireDataset(datasetId);
        if (!taskRepository.existsById(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (!itemRepository.existsByDatasetIdAndTaskId(datasetId, taskId)) {
            EvalDatasetItemEntity item = new EvalDatasetItemEntity();
            item.setDatasetId(datasetId);
            item.setTaskId(taskId);
            item.setAddedAt(Instant.now());
            itemRepository.save(item);
        }
        EvalDatasetEntity entity = requireDataset(datasetId);
        entity.setUpdatedAt(Instant.now());
        return toGraphql(datasetRepository.save(entity));
    }

    @Transactional
    public EvalDataset addFailedEvalTasks(UUID datasetId, Instant from, Instant to) {
        EvalDatasetEntity entity = requireDataset(datasetId);
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minusSeconds(30L * 24 * 3600);
        List<EvalRunEntity> failed = evalRunRepository.findCompletedInRange(resolvedFrom, resolvedTo, EvalRunStatus.COMPLETE)
                .stream()
                .filter(r -> r.getLabel() == EvalRunLabel.FAIL)
                .toList();
        for (EvalRunEntity run : failed) {
            if (!itemRepository.existsByDatasetIdAndTaskId(datasetId, run.getTaskId())) {
                EvalDatasetItemEntity item = new EvalDatasetItemEntity();
                item.setDatasetId(datasetId);
                item.setTaskId(run.getTaskId());
                item.setAddedAt(Instant.now());
                itemRepository.save(item);
            }
        }
        entity.setSource(EvalDatasetSource.FROM_FAILED_EVALS);
        entity.setUpdatedAt(Instant.now());
        return toGraphql(datasetRepository.save(entity));
    }

    @Transactional
    public EvalDataset populateFromFilter(UUID datasetId, SourceTool sourceTool, Integer minRetries, Instant from, Instant to) {
        EvalDatasetEntity entity = requireDataset(datasetId);
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minusSeconds(30L * 24 * 3600);
        List<TaskEntity> tasks = taskRepository.findAll(TaskSpecifications.filtered(
                null, sourceTool, resolvedFrom, resolvedTo, minRetries, null, null, null, null
        ));
        for (TaskEntity task : tasks) {
            if (!itemRepository.existsByDatasetIdAndTaskId(datasetId, task.getId())) {
                EvalDatasetItemEntity item = new EvalDatasetItemEntity();
                item.setDatasetId(datasetId);
                item.setTaskId(task.getId());
                item.setAddedAt(Instant.now());
                itemRepository.save(item);
            }
        }
        entity.setSource(EvalDatasetSource.FROM_FILTER);
        entity.setUpdatedAt(Instant.now());
        return toGraphql(datasetRepository.save(entity));
    }

    private EvalDatasetEntity requireDataset(UUID id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }

    private EvalDataset toGraphql(EvalDatasetEntity entity) {
        return new EvalDataset(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getSource(),
                entity.getFilterJson(),
                itemRepository.countByDatasetId(entity.getId()),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
