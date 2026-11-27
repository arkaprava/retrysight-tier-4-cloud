/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.SessionFeedbackEntity;
import com.agentretry.manager.graphql.SessionFeedback;
import com.agentretry.manager.repository.SessionFeedbackRepository;
import com.agentretry.manager.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionFeedbackService {
    private final SessionFeedbackRepository repository;
    private final TaskRepository taskRepository;

    public SessionFeedbackService(SessionFeedbackRepository repository, TaskRepository taskRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<SessionFeedback> listForTask(UUID taskId) {
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional
    public SessionFeedback submit(
            UUID taskId,
            UUID eventId,
            int rating,
            String comment,
            FeedbackType feedbackType,
            String createdBy
    ) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        SessionFeedbackEntity entity = new SessionFeedbackEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setEventId(eventId);
        entity.setRating((short) Math.max(-1, Math.min(1, rating)));
        entity.setComment(comment);
        entity.setFeedbackType(feedbackType != null ? feedbackType : FeedbackType.THUMBS);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        return toGraphql(repository.save(entity));
    }

    private SessionFeedback toGraphql(SessionFeedbackEntity entity) {
        return new SessionFeedback(
                entity.getId(),
                entity.getTaskId(),
                entity.getEventId(),
                entity.getRating(),
                entity.getComment(),
                entity.getFeedbackType(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}
