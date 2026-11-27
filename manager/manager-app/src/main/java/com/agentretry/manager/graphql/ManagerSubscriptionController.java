/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.service.QueryService;
import com.agentretry.manager.service.TaskUpdatePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Controller
public class ManagerSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(ManagerSubscriptionController.class);

    private final TaskUpdatePublisher publisher;
    private final QueryService queryService;

    public ManagerSubscriptionController(TaskUpdatePublisher publisher, QueryService queryService) {
        this.publisher = publisher;
        this.queryService = queryService;
    }

    @SubscriptionMapping
    public Flux<TaskEntity> taskUpdated(@Argument UUID agentId) {
        log.debug("taskUpdated subscription started agentId={}", agentId);
        return publisher.taskUpdates(agentId)
                .doOnSubscribe(sub -> log.debug("taskUpdated flux subscribed"))
                .doOnComplete(() -> log.debug("taskUpdated flux completed"))
                .doOnCancel(() -> log.debug("taskUpdated flux cancelled"))
                .map(task -> queryService.findTask(task.getId()))
                .filter(task -> task != null);
    }

    @SubscriptionMapping
    public Flux<AgentEntity> agentHeartbeat() {
        log.debug("agentHeartbeat subscription started");
        return publisher.agentHeartbeats()
                .doOnSubscribe(sub -> log.debug("agentHeartbeat flux subscribed"))
                .doOnComplete(() -> log.debug("agentHeartbeat flux completed"))
                .doOnCancel(() -> log.debug("agentHeartbeat flux cancelled"));
    }
}
