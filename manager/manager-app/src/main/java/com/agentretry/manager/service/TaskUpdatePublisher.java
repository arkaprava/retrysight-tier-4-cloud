/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

@Component
public class TaskUpdatePublisher {

    private final Sinks.Many<TaskEntity> taskSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<AgentEntity> agentSink = Sinks.many().multicast().onBackpressureBuffer();

    public void publishTask(TaskEntity task) {
        taskSink.tryEmitNext(task);
    }

    public void publishAgent(AgentEntity agent) {
        agentSink.tryEmitNext(agent);
    }

    public Flux<TaskEntity> taskUpdates(UUID agentId) {
        if (agentId == null) {
            return taskSink.asFlux();
        }
        return taskSink.asFlux()
                .filter(task -> task.getAgent() != null && agentId.equals(task.getAgent().getId()));
    }

    public Flux<AgentEntity> agentHeartbeats() {
        return agentSink.asFlux();
    }
}
