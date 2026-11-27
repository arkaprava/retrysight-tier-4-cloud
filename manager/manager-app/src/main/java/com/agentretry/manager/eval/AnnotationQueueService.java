/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.AnnotationQueueItemEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.graphql.AnnotationQueueItem;
import com.agentretry.manager.repository.AnnotationQueueRepository;
import com.agentretry.manager.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AnnotationQueueService {
    private final AnnotationQueueRepository repository;
    private final TaskRepository taskRepository;

    public AnnotationQueueService(AnnotationQueueRepository repository, TaskRepository taskRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<AnnotationQueueItem> list(AnnotationQueueStatus status) {
        List<AnnotationQueueItemEntity> entities = status != null
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        return entities.stream().map(this::toGraphql).toList();
    }

    @Transactional
    public AnnotationQueueItem enqueue(UUID taskId, AnnotationQueueReason reason, String createdBy) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        AnnotationQueueItemEntity entity = new AnnotationQueueItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setReason(reason != null ? reason : AnnotationQueueReason.MANUAL);
        entity.setStatus(AnnotationQueueStatus.OPEN);
        entity.setCreatedBy(createdBy != null ? createdBy : "unknown");
        entity.setCreatedAt(Instant.now());
        return toGraphql(repository.save(entity));
    }

    @Transactional
    public void enqueueIfAbsent(UUID taskId, AnnotationQueueReason reason, String createdBy) {
        if (repository.existsByTaskIdAndStatus(taskId, AnnotationQueueStatus.OPEN)) {
            return;
        }
        enqueue(taskId, reason, createdBy);
    }

    @Transactional
    public AnnotationQueueItem complete(UUID id, AnnotationQueueStatus status, String notes, String actor) {
        AnnotationQueueItemEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Queue item not found"));
        entity.setStatus(status != null ? status : AnnotationQueueStatus.DONE);
        entity.setNotes(notes);
        if (entity.getAssignedTo() == null && actor != null) {
            entity.setAssignedTo(actor);
        }
        entity.setCompletedAt(Instant.now());
        return toGraphql(repository.save(entity));
    }

    private AnnotationQueueItem toGraphql(AnnotationQueueItemEntity entity) {
        return new AnnotationQueueItem(
                entity.getId(),
                entity.getTaskId(),
                entity.getReason(),
                entity.getAssignedTo(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }
}
