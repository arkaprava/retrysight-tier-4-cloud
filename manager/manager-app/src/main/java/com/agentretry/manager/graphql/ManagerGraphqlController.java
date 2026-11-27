/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.AgentEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.git.ProjectContextService;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.replay.SessionReplayService;
import com.agentretry.manager.service.QueryService;
import com.agentretry.manager.trace.SpanService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ManagerGraphqlController {

    private final QueryService queryService;
    private final SessionReplayService sessionReplayService;
    private final ProjectContextService projectContextService;
    private final SpanService spanService;
    private final FeatureGateService featureGateService;

    public ManagerGraphqlController(
            QueryService queryService,
            SessionReplayService sessionReplayService,
            ProjectContextService projectContextService,
            SpanService spanService,
            FeatureGateService featureGateService
    ) {
        this.queryService = queryService;
        this.sessionReplayService = sessionReplayService;
        this.projectContextService = projectContextService;
        this.spanService = spanService;
        this.featureGateService = featureGateService;
    }

    @QueryMapping
    public List<UserSummary> users() {
        return queryService.findUsers();
    }

    @QueryMapping
    public List<AgentEntity> agents() {
        return queryService.findAgents();
    }

    @QueryMapping
    public List<AgentEntity> staleAgents() {
        return queryService.findStaleAgents();
    }

    @QueryMapping
    public AgentEntity agent(@Argument UUID id) {
        return queryService.findAgent(id);
    }

    @SchemaMapping(typeName = "Agent", field = "stale")
    public boolean agentStale(AgentEntity agent) {
        return queryService.isAgentStale(agent);
    }

    @QueryMapping
    public TaskConnection tasks(
            @Argument UUID agentId,
            @Argument SourceTool sourceTool,
            @Argument Instant from,
            @Argument Instant to,
            @Argument Integer minRetries,
            @Argument String user,
            @Argument String repo,
            @Argument String branch,
            @Argument String remoteHost,
            @Argument Integer page,
            @Argument Integer size
    ) {
        return queryService.findTasks(
                agentId, sourceTool, from, to, minRetries, user, repo, branch, remoteHost, page, size
        );
    }

    @QueryMapping
    public TaskEntity task(@Argument UUID id) {
        return queryService.findTask(id);
    }

    @QueryMapping
    public DashboardSummary dashboardSummary(
            @Argument Instant from,
            @Argument Instant to,
            @Argument String user
    ) {
        return queryService.dashboardSummary(from, to, user);
    }

    @SchemaMapping(typeName = "Task", field = "projectContext")
    public ProjectContext taskProjectContext(TaskEntity task) {
        return projectContextService.resolve(task);
    }

    @SchemaMapping(typeName = "Task", field = "tokenUsage")
    public TokenUsageSummary taskTokenUsage(TaskEntity task) {
        return queryService.summarizeTaskTokens(task.getId());
    }

    @SchemaMapping(typeName = "Task", field = "events")
    public List<TaskEventEntity> taskEvents(TaskEntity task) {
        return queryService.findTaskEvents(task.getId());
    }

    @SchemaMapping(typeName = "Task", field = "timeline")
    public List<TaskEventEntity> taskTimeline(TaskEntity task) {
        return queryService.findTaskEvents(task.getId());
    }

    @SchemaMapping(typeName = "Task", field = "tokenTurns")
    public List<TokenUsageEntity> taskTokenTurns(TaskEntity task) {
        return queryService.findTokenUsage(task.getId());
    }

    @SchemaMapping(typeName = "Task", field = "sessionReplay")
    public String taskSessionReplay(TaskEntity task, @Argument SessionReplayFormat format) {
        SessionReplayFormat resolved = format != null ? format : SessionReplayFormat.PROMPT;
        return sessionReplayService.render(task.getId(), resolved);
    }

    @SchemaMapping(typeName = "Task", field = "spanTree")
    public List<SpanNode> taskSpanTree(TaskEntity task) {
        if (featureGateService.isTraceQueryEnabled()) {
            List<SpanNode> otelSpans = spanService.spanTreeForTask(task.getId(), task.getStartedAt());
            if (!otelSpans.isEmpty()) {
                return otelSpans;
            }
        }
        List<TaskEventEntity> events = queryService.findTaskEvents(task.getId());
        if (events.isEmpty()) {
            return List.of();
        }
        Instant base = events.getFirst().getOccurredAt();
        UUID rootId = events.getFirst().getId();
        java.util.ArrayList<SpanNode> spans = new java.util.ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            TaskEventEntity event = events.get(i);
            long startMs = Math.max(0, event.getOccurredAt().toEpochMilli() - base.toEpochMilli());
            long endMs = i + 1 < events.size()
                    ? events.get(i + 1).getOccurredAt().toEpochMilli() - base.toEpochMilli()
                    : startMs + 250;
            long durationMs = Math.max(50, endMs - startMs);
            UUID parentId = i == 0 ? null : rootId;
            spans.add(new SpanNode(
                    event.getId(),
                    event.getEventType().name(),
                    event.getEventType().name(),
                    startMs,
                    durationMs,
                    event.isRetry() ? "retry" : "ok",
                    parentId,
                    event.getPayload()
            ));
        }
        return spans;
    }

    @SchemaMapping(typeName = "Task", field = "agent")
    public AgentEntity taskAgent(TaskEntity task) {
        return task.getAgent();
    }

    @BatchMapping(typeName = "TaskEvent", field = "tokenUsage")
    public Map<TaskEventEntity, EventTokenUsage> taskEventTokenUsage(List<TaskEventEntity> events) {
        return queryService.resolveEventTokenUsage(events);
    }

    @BatchMapping(typeName = "Task", field = "modelsUsed")
    public Map<TaskEntity, List<String>> taskModelsUsed(List<TaskEntity> tasks) {
        return queryService.resolveModelsUsed(tasks);
    }

    @BatchMapping(typeName = "Task", field = "latestModel")
    public Map<TaskEntity, String> taskLatestModel(List<TaskEntity> tasks) {
        return queryService.resolveLatestModels(tasks);
    }

    @SchemaMapping(typeName = "EventTokenUsage", field = "totalTokens")
    public long eventTokenUsageTotal(EventTokenUsage usage) {
        return usage.totalTokens();
    }
}
