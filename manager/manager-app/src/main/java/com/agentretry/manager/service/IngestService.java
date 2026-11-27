/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.contract.*;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TaskIdAliasEntity;
import com.agentretry.manager.domain.TokenSource;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.repository.AgentRepository;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskIdAliasRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TokenUsageRepository;
import com.agentretry.manager.clickhouse.AnalyticsSinkPublisher;
import com.agentretry.manager.eval.OnlineEvalDispatcher;
import com.agentretry.manager.git.ProjectContextService;
import com.agentretry.manager.security.ApiKeyHasher;
import com.agentretry.manager.security.PayloadRedactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IngestService {

    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final TaskIdAliasRepository taskIdAliasRepository;
    private final TaskEventRepository taskEventRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ObjectMapper objectMapper;
    private final TaskUpdatePublisher taskUpdatePublisher;
    private final ApiKeyHasher apiKeyHasher;
    private final ModelPricingService modelPricingService;
    private final ProjectContextService projectContextService;
    private final AnalyticsSinkPublisher analyticsSinkPublisher;
    private final OnlineEvalDispatcher onlineEvalDispatcher;
    private final PayloadRedactionService payloadRedactionService;

    public IngestService(
            AgentRepository agentRepository,
            TaskRepository taskRepository,
            TaskIdAliasRepository taskIdAliasRepository,
            TaskEventRepository taskEventRepository,
            TokenUsageRepository tokenUsageRepository,
            ObjectMapper objectMapper,
            TaskUpdatePublisher taskUpdatePublisher,
            ApiKeyHasher apiKeyHasher,
            ModelPricingService modelPricingService,
            ProjectContextService projectContextService,
            AnalyticsSinkPublisher analyticsSinkPublisher,
            OnlineEvalDispatcher onlineEvalDispatcher,
            PayloadRedactionService payloadRedactionService
    ) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.taskIdAliasRepository = taskIdAliasRepository;
        this.taskEventRepository = taskEventRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.objectMapper = objectMapper;
        this.taskUpdatePublisher = taskUpdatePublisher;
        this.apiKeyHasher = apiKeyHasher;
        this.modelPricingService = modelPricingService;
        this.projectContextService = projectContextService;
        this.analyticsSinkPublisher = analyticsSinkPublisher;
        this.onlineEvalDispatcher = onlineEvalDispatcher;
        this.payloadRedactionService = payloadRedactionService;
    }

    @Transactional
    public IngestBatchResponse processBatch(UUID agentId, IngestBatchRequest request) {
        AgentEntity agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + agentId));

        if (request.heartbeat() != null) {
            updateHeartbeat(agent, request.heartbeat());
        }

        int accepted = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        if (request.taskStarts() != null) {
            for (TaskStartRequest start : request.taskStarts()) {
                try {
                    startTask(agent, start);
                    accepted++;
                } catch (Exception ex) {
                    errors.add("taskStart " + start.taskId() + ": " + ex.getMessage());
                }
            }
        }

        if (request.items() != null) {
            List<IngestBatchItem> taskStarts = new ArrayList<>();
            List<IngestBatchItem> otherItems = new ArrayList<>();
            for (IngestBatchItem item : request.items()) {
                if (item.type() == IngestItemType.TASK_START) {
                    taskStarts.add(item);
                } else {
                    otherItems.add(item);
                }
            }

            for (IngestBatchItem item : taskStarts) {
                try {
                    boolean processed = processItem(agent, item);
                    if (processed) {
                        accepted++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    errors.add(item.idempotencyKey() + ": " + ex.getMessage());
                }
            }

            for (IngestBatchItem item : otherItems) {
                try {
                    boolean processed = processItem(agent, item);
                    if (processed) {
                        accepted++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    errors.add(item.idempotencyKey() + ": " + ex.getMessage());
                }
            }
        }

        return new IngestBatchResponse(accepted, skipped, errors, agentId);
    }

    /**
     * Resolves an agent by durable UUID when {@code agentId} is present and known;
     * otherwise enrolls / looks up by hostname + developerEmail.
     */
    @Transactional
    public AgentEntity registerOrResolveAgent(String apiKey, UUID agentId, AgentHeartbeatRequest heartbeat) {
        if (agentId != null) {
            Optional<AgentEntity> byId = agentRepository.findById(agentId);
            if (byId.isPresent()) {
                AgentEntity agent = byId.get();
                verifyAgentAccess(apiKey, agent, heartbeat);
                if (heartbeat != null) {
                    applyIdentityUpdate(agent, heartbeat);
                    agentRepository.save(agent);
                }
                taskUpdatePublisher.publishAgent(agent);
                return agent;
            }
        }

        if (heartbeat == null
                || heartbeat.hostname() == null || heartbeat.hostname().isBlank()
                || heartbeat.developerEmail() == null || heartbeat.developerEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Unknown X-Agent-Id and heartbeat hostname/developerEmail required for enrollment");
        }

        AgentEntity agent = agentRepository.findByHostnameAndDeveloperEmail(
                        heartbeat.hostname(), heartbeat.developerEmail())
                .orElseGet(() -> {
                    AgentEntity created = new AgentEntity();
                    created.setId(UUID.randomUUID());
                    created.setName(heartbeat.hostname());
                    created.setHostname(heartbeat.hostname());
                    created.setDeveloperEmail(heartbeat.developerEmail());
                    applyUserInfo(created, heartbeat);
                    created.setApiKeyHash(apiKeyHasher.hash(apiKey));
                    created.setCreatedAt(Instant.now());
                    return agentRepository.save(created);
                });
        if (heartbeat != null) {
            applyUserInfo(agent, heartbeat);
            agentRepository.save(agent);
        }
        taskUpdatePublisher.publishAgent(agent);
        return agent;
    }

    /** Backward-compatible enrollment path (hostname + email only). */
    @Transactional
    public AgentEntity registerOrResolveAgent(String apiKey, AgentHeartbeatRequest heartbeat) {
        return registerOrResolveAgent(apiKey, null, heartbeat);
    }

    private void updateHeartbeat(AgentEntity agent, AgentHeartbeatRequest heartbeat) {
        agent.setLastHeartbeatAt(Optional.ofNullable(heartbeat.reportedAt()).orElse(Instant.now()));
        applyIdentityUpdate(agent, heartbeat);
        if (heartbeat.activeCollectors() != null) {
            agent.setInstalledCollectors(writeJson(heartbeat.activeCollectors()));
        }
        agentRepository.save(agent);
        taskUpdatePublisher.publishAgent(agent);
    }

    /**
     * Updates identity fields for a known agent without creating a duplicate row.
     * Hostname/email changes are applied only when they do not collide with another agent.
     */
    private void applyIdentityUpdate(AgentEntity agent, AgentHeartbeatRequest heartbeat) {
        applyUserInfo(agent, heartbeat);
        String newHost = heartbeat.hostname();
        String newEmail = heartbeat.developerEmail();
        if (newHost == null || newHost.isBlank() || newEmail == null || newEmail.isBlank()) {
            return;
        }
        String host = newHost.trim();
        String email = newEmail.trim();
        boolean hostChanged = !host.equals(agent.getHostname());
        boolean emailChanged = !email.equals(agent.getDeveloperEmail());
        if (!hostChanged && !emailChanged) {
            return;
        }
        Optional<AgentEntity> conflict = agentRepository.findByHostnameAndDeveloperEmail(host, email);
        if (conflict.isPresent() && !conflict.get().getId().equals(agent.getId())) {
            return;
        }
        agent.setHostname(host);
        agent.setName(host);
        agent.setDeveloperEmail(email);
    }

    private void applyUserInfo(AgentEntity agent, AgentHeartbeatRequest heartbeat) {
        if (heartbeat.osUsername() != null && !heartbeat.osUsername().isBlank()) {
            agent.setOsUsername(heartbeat.osUsername().trim());
        }
        if (heartbeat.displayName() != null && !heartbeat.displayName().isBlank()) {
            agent.setDisplayName(heartbeat.displayName().trim());
        }
        if (heartbeat.gitEmail() != null && !heartbeat.gitEmail().isBlank()) {
            agent.setGitEmail(heartbeat.gitEmail().trim());
        }
        if (heartbeat.developerEmail() != null && !heartbeat.developerEmail().isBlank()
                && (agent.getDeveloperEmail() == null || agent.getDeveloperEmail().isBlank())) {
            agent.setDeveloperEmail(heartbeat.developerEmail().trim());
        }
    }

    private void startTask(AgentEntity agent, TaskStartRequest request) {
        if (taskRepository.existsById(request.taskId())) {
            return;
        }
        // Same IDE session must map to one task even if collectors send different task ids.
        if (request.sourceTool() != null
                && request.externalSessionId() != null
                && !request.externalSessionId().isBlank()) {
            Optional<TaskEntity> existingSession = taskRepository.findBySourceToolAndExternalSessionId(
                    request.sourceTool(), request.externalSessionId());
            if (existingSession.isPresent()) {
                rememberAlias(request.taskId(), existingSession.get().getId());
                return;
            }
        }

        TaskEntity task = new TaskEntity();
        task.setId(request.taskId());
        task.setAgent(agent);
        task.setOrgId(agent.getOrgId());
        task.setExternalSessionId(request.externalSessionId());
        task.setSourceTool(request.sourceTool());
        task.setDescription(truncate(request.description(), 2000));
        task.setStatus(TaskStatus.ACTIVE);
        task.setRetryCount(0);
        task.setStartedAt(Optional.ofNullable(request.startedAt()).orElse(Instant.now()));
        if (request.metadata() != null) {
            task.setMetadata(writeJson(request.metadata()));
            projectContextService.applyDenormalizedColumns(task, request.metadata());
        }
        taskRepository.save(task);
        publishTask(task.getId());
    }

    private void rememberAlias(UUID aliasId, UUID canonicalTaskId) {
        if (aliasId.equals(canonicalTaskId) || taskIdAliasRepository.existsById(aliasId)) {
            return;
        }
        TaskIdAliasEntity alias = new TaskIdAliasEntity();
        alias.setAliasId(aliasId);
        alias.setCanonicalTaskId(canonicalTaskId);
        taskIdAliasRepository.save(alias);
    }

    private TaskEntity requireTask(UUID taskId) {
        Optional<TaskEntity> direct = taskRepository.findById(taskId);
        if (direct.isPresent()) {
            return direct.get();
        }
        return taskIdAliasRepository.findById(taskId)
                .flatMap(alias -> taskRepository.findById(alias.getCanonicalTaskId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
    }

    private boolean processItem(AgentEntity agent, IngestBatchItem item) {
        return switch (item.type()) {
            case TASK_START -> {
                startTask(agent, convert(item.payload(), TaskStartRequest.class));
                yield true;
            }
            case TASK_EVENT -> {
                TaskEventRequest event = convert(item.payload(), TaskEventRequest.class);
                yield recordEvent(agent, event, item.idempotencyKey());
            }
            case TOKEN_USAGE -> {
                TokenUsageRequest usage = convert(item.payload(), TokenUsageRequest.class);
                yield recordTokenUsage(agent, usage, item.idempotencyKey());
            }
            case TASK_COMPLETE -> {
                TaskCompleteRequest complete = convert(item.payload(), TaskCompleteRequest.class);
                yield completeTask(agent, complete, item.idempotencyKey());
            }
        };
    }

    private void verifyAgentAccess(String apiKey, AgentEntity agent, AgentHeartbeatRequest heartbeat) {
        String storedHash = agent.getApiKeyHash();
        if (storedHash != null && !storedHash.isBlank()) {
            if (!apiKeyHasher.matches(apiKey, storedHash)) {
                throw new SecurityException("API key does not match agent identity");
            }
        } else {
            agent.setApiKeyHash(apiKeyHasher.hash(apiKey));
        }
        if (heartbeat != null) {
            verifyHeartbeatIdentity(agent, heartbeat);
        }
    }

    private void verifyHeartbeatIdentity(AgentEntity agent, AgentHeartbeatRequest heartbeat) {
        String host = heartbeat.hostname();
        String email = heartbeat.developerEmail();
        if (host == null || host.isBlank() || email == null || email.isBlank()) {
            return;
        }
        String hostTrim = host.trim();
        String emailTrim = email.trim();
        if (hostTrim.equalsIgnoreCase(agent.getHostname().trim())
                && emailTrim.equalsIgnoreCase(agent.getDeveloperEmail().trim())) {
            return;
        }
        Optional<AgentEntity> conflict = agentRepository.findByHostnameAndDeveloperEmail(hostTrim, emailTrim);
        if (conflict.isPresent() && !conflict.get().getId().equals(agent.getId())) {
            throw new SecurityException("Heartbeat identity is registered to another agent");
        }
    }

    private TaskEntity requireTaskOwnedByAgent(AgentEntity agent, UUID taskId) {
        TaskEntity task = requireTask(taskId);
        if (task.getAgent() == null || !task.getAgent().getId().equals(agent.getId())) {
            throw new SecurityException("Task " + taskId + " does not belong to agent " + agent.getId());
        }
        return task;
    }

    private boolean recordEvent(AgentEntity agent, TaskEventRequest request, String idempotencyKey) {
        if (taskEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            return false;
        }

        TaskEntity task = requireTaskOwnedByAgent(agent, request.taskId());

        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setTask(task);
        event.setEventType(request.eventType());
        event.setOccurredAt(request.occurredAt());
        event.setRetry(request.retry());
        event.setSequenceNum(request.sequenceNum());
        event.setIdempotencyKey(idempotencyKey);
        if (request.payload() != null) {
            event.setPayload(writeJson(request.payload()));
            if (request.eventType() == TaskEventType.EDIT) {
                projectContextService.backfillFromEventPayload(task, request.payload());
                taskRepository.save(task);
            }
        }
        if (event.getPayload() != null) {
            event.setPayload(payloadRedactionService.redact(event.getPayload()));
        }
        taskEventRepository.save(event);
        analyticsSinkPublisher.publishTaskEventAfterCommit(event);

        if (request.retry()) {
            task.setRetryCount(task.getRetryCount() + 1);
            taskRepository.save(task);
        }
        publishTask(task.getId());
        return true;
    }

    private boolean recordTokenUsage(AgentEntity agent, TokenUsageRequest request, String idempotencyKey) {
        if (tokenUsageRepository.existsByIdempotencyKey(idempotencyKey)) {
            return false;
        }

        TaskEntity task = requireTaskOwnedByAgent(agent, request.taskId());

        TokenUsageEntity usage = new TokenUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setTask(task);
        usage.setTurnIndex(request.turnIndex());
        usage.setModel(request.model());
        usage.setInputTokens(request.inputTokens());
        usage.setOutputTokens(request.outputTokens());
        usage.setCacheReadTokens(request.cacheReadTokens());
        usage.setCacheWriteTokens(request.cacheWriteTokens());
        usage.setReasoningTokens(request.reasoningTokens());
        usage.setEstimatedCostUsd(resolveEstimatedCost(request));
        usage.setTokenSource(resolveTokenSource(request));
        usage.setRecordedAt(Optional.ofNullable(request.recordedAt()).orElse(Instant.now()));
        usage.setIdempotencyKey(idempotencyKey);
        tokenUsageRepository.save(usage);
        analyticsSinkPublisher.publishTokenUsageAfterCommit(usage);
        publishTask(task.getId());
        return true;
    }

    private boolean completeTask(AgentEntity agent, TaskCompleteRequest request, String idempotencyKey) {
        if (taskEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            return false;
        }

        TaskEntity task = requireTaskOwnedByAgent(agent, request.taskId());

        task.setStatus(request.status());
        task.setRetryCount(request.retryCount());
        task.setCompletedAt(Optional.ofNullable(request.completedAt()).orElse(Instant.now()));
        taskRepository.save(task);

        TaskEventEntity event = new TaskEventEntity();
        event.setId(UUID.randomUUID());
        event.setTask(task);
        event.setEventType(TaskEventType.SESSION_END);
        event.setOccurredAt(task.getCompletedAt());
        event.setRetry(false);
        event.setSequenceNum(Integer.MAX_VALUE);
        event.setIdempotencyKey(idempotencyKey);
        taskEventRepository.save(event);
        analyticsSinkPublisher.publishTaskEventAfterCommit(event);
        publishTask(task.getId());
        onlineEvalDispatcher.scheduleAfterTaskComplete(task.getId());
        return true;
    }

    private void publishTask(UUID taskId) {
        taskRepository.findById(taskId).ifPresent(taskUpdatePublisher::publishTask);
    }

    private <T> T convert(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload, type);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private BigDecimal resolveEstimatedCost(TokenUsageRequest request) {
        if (request.estimatedCostUsd() != null && request.estimatedCostUsd() > 0) {
            return BigDecimal.valueOf(request.estimatedCostUsd());
        }
        return modelPricingService.estimateCost(
                request.model(),
                request.inputTokens(),
                request.outputTokens(),
                request.cacheReadTokens(),
                request.cacheWriteTokens(),
                request.reasoningTokens()
        );
    }

    private TokenSource resolveTokenSource(TokenUsageRequest request) {
        if (request.estimatedCostUsd() != null && request.estimatedCostUsd() > 0) {
            return TokenSource.PROVIDER;
        }
        return TokenSource.ESTIMATED;
    }
}
