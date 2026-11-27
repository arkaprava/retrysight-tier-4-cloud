/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.trace.SpanService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
public class TraceGraphqlController {
    private final FeatureGateService featureGateService;
    private final SpanService spanService;

    public TraceGraphqlController(FeatureGateService featureGateService, SpanService spanService) {
        this.featureGateService = featureGateService;
        this.spanService = spanService;
    }

    private void requireTraceQuery() {
        if (!featureGateService.isTraceQueryEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trace query is not enabled for this installation");
        }
    }

    @QueryMapping
    public List<TraceSpan> traceSpans(
            @Argument UUID taskId,
            @Argument SourceTool sourceTool,
            @Argument Instant from,
            @Argument Instant to
    ) {
        requireTraceQuery();
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minusSeconds(7L * 24 * 3600);
        String tool = sourceTool != null ? sourceTool.name() : null;
        return spanService.search(taskId, tool, resolvedFrom, resolvedTo);
    }

    @SchemaMapping(typeName = "Task", field = "traceSpans")
    public List<TraceSpan> taskTraceSpans(TaskEntity task) {
        if (!featureGateService.isTraceQueryEnabled()) {
            return List.of();
        }
        return spanService.listForTask(task.getId());
    }
}
