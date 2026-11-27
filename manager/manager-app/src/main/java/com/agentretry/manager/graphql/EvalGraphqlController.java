/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.eval.AnnotationQueueReason;
import com.agentretry.manager.eval.AnnotationQueueService;
import com.agentretry.manager.eval.AnnotationQueueStatus;
import com.agentretry.manager.eval.EvalAnalyticsService;
import com.agentretry.manager.eval.EvalDatasetService;
import com.agentretry.manager.eval.EvalDatasetSource;
import com.agentretry.manager.eval.EvalDefinitionKind;
import com.agentretry.manager.eval.EvalDefinitionService;
import com.agentretry.manager.eval.EvalExperimentService;
import com.agentretry.manager.eval.EvalPlaygroundService;
import com.agentretry.manager.eval.EvalRunnerService;
import com.agentretry.manager.eval.EvalTargetScope;
import com.agentretry.manager.eval.FeedbackType;
import com.agentretry.manager.eval.PatternClusteringService;
import com.agentretry.manager.eval.SessionFeedbackService;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.security.RoleGuard;
import com.agentretry.manager.service.EvalScorecardService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
public class EvalGraphqlController {

    private final FeatureGateService featureGateService;
    private final EvalDefinitionService definitionService;
    private final EvalRunnerService runnerService;
    private final EvalAnalyticsService analyticsService;
    private final AnnotationQueueService annotationQueueService;
    private final EvalScorecardService scorecardService;
    private final EvalDatasetService datasetService;
    private final EvalExperimentService experimentService;
    private final EvalPlaygroundService playgroundService;
    private final SessionFeedbackService sessionFeedbackService;
    private final PatternClusteringService patternClusteringService;

    public EvalGraphqlController(
            FeatureGateService featureGateService,
            EvalDefinitionService definitionService,
            EvalRunnerService runnerService,
            EvalAnalyticsService analyticsService,
            AnnotationQueueService annotationQueueService,
            EvalScorecardService scorecardService,
            EvalDatasetService datasetService,
            EvalExperimentService experimentService,
            EvalPlaygroundService playgroundService,
            SessionFeedbackService sessionFeedbackService,
            PatternClusteringService patternClusteringService
    ) {
        this.featureGateService = featureGateService;
        this.definitionService = definitionService;
        this.runnerService = runnerService;
        this.analyticsService = analyticsService;
        this.annotationQueueService = annotationQueueService;
        this.scorecardService = scorecardService;
        this.datasetService = datasetService;
        this.experimentService = experimentService;
        this.playgroundService = playgroundService;
        this.sessionFeedbackService = sessionFeedbackService;
        this.patternClusteringService = patternClusteringService;
    }

    private void requireEvaluations() {
        if (!featureGateService.isEvaluationsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evaluations are not enabled for this installation");
        }
    }

    private void requireExperiments() {
        if (!featureGateService.isEvalExperimentsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eval experiments are not enabled for this installation");
        }
    }

    private void requirePatterns() {
        if (!featureGateService.isPatternsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pattern clustering is not enabled for this installation");
        }
    }

    @QueryMapping
    public List<EvalDefinition> evalDefinitions() {
        requireEvaluations();
        return definitionService.list();
    }

    @QueryMapping
    public List<EvalRun> evalRuns(@Argument UUID taskId) {
        requireEvaluations();
        return analyticsService.listRuns(taskId);
    }

    @QueryMapping
    public EvalSummary evalSummary(@Argument Instant from, @Argument Instant to) {
        requireEvaluations();
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minusSeconds(7 * 24 * 3600L);
        return analyticsService.summary(resolvedFrom, resolvedTo);
    }

    @QueryMapping
    public List<AnnotationQueueItem> annotationQueue(@Argument AnnotationQueueStatus status) {
        requireEvaluations();
        return annotationQueueService.list(status);
    }

    @QueryMapping
    public List<EvalScorecard> evalScorecards(@Argument UUID taskId) {
        requireEvaluations();
        return scorecardService.list(taskId);
    }

    @SchemaMapping(typeName = "Task", field = "evalRuns")
    public List<EvalRun> taskEvalRuns(com.agentretry.manager.domain.TaskEntity task) {
        if (!featureGateService.isEvaluationsEnabled()) {
            return List.of();
        }
        return analyticsService.listRuns(task.getId());
    }

    @SchemaMapping(typeName = "Task", field = "sessionFeedback")
    public List<SessionFeedback> taskSessionFeedback(com.agentretry.manager.domain.TaskEntity task) {
        if (!featureGateService.isEvaluationsEnabled()) {
            return List.of();
        }
        return sessionFeedbackService.listForTask(task.getId());
    }

    @QueryMapping
    public List<EvalDataset> evalDatasets() {
        requireExperiments();
        return datasetService.list();
    }

    @QueryMapping
    public EvalDataset evalDataset(@Argument UUID id) {
        requireExperiments();
        return datasetService.get(id);
    }

    @QueryMapping
    public List<EvalExperiment> evalExperiments() {
        requireExperiments();
        return experimentService.list();
    }

    @QueryMapping
    public List<EvalExperimentResult> evalExperimentResults(@Argument UUID experimentId) {
        requireExperiments();
        return experimentService.results(experimentId);
    }

    @QueryMapping
    public List<TopicPattern> topicPatterns() {
        requirePatterns();
        return patternClusteringService.latestPatterns();
    }

    @QueryMapping
    public List<SessionFeedback> sessionFeedback(@Argument UUID taskId) {
        requireEvaluations();
        return sessionFeedbackService.listForTask(taskId);
    }

    @MutationMapping
    public EvalDefinition createEvalDefinition(
            Authentication authentication,
            @Argument String slug,
            @Argument String name,
            @Argument EvalDefinitionKind kind,
            @Argument String promptTemplate,
            @Argument String ruleConfig,
            @Argument EvalTargetScope targetScope,
            @Argument Boolean enabled
    ) {
        requireEvaluations();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return definitionService.create(slug, name, kind, promptTemplate, ruleConfig, targetScope,
                enabled == null || enabled);
    }

    @MutationMapping
    public EvalDefinition updateEvalDefinition(
            Authentication authentication,
            @Argument UUID id,
            @Argument String name,
            @Argument String promptTemplate,
            @Argument String ruleConfig,
            @Argument Boolean enabled
    ) {
        requireEvaluations();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return definitionService.update(id, name, promptTemplate, ruleConfig, enabled);
    }

    @MutationMapping
    public boolean deleteEvalDefinition(Authentication authentication, @Argument UUID id) {
        requireEvaluations();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return definitionService.delete(id);
    }

    @MutationMapping
    public List<EvalRun> runTaskEvaluations(Authentication authentication, @Argument UUID taskId) {
        requireEvaluations();
        RoleGuard.requireAdminOrSecadmin(authentication);
        runnerService.runManualEvaluations(taskId);
        return analyticsService.listRuns(taskId);
    }

    @MutationMapping
    public EvalScorecard createEvalScorecard(
            Authentication authentication,
            @Argument UUID taskId,
            @Argument double score,
            @Argument String notes
    ) {
        requireEvaluations();
        String email = RoleGuard.requireAuthenticated(authentication);
        return scorecardService.create(taskId, score, notes, email);
    }

    @MutationMapping
    public AnnotationQueueItem enqueueAnnotation(
            Authentication authentication,
            @Argument UUID taskId,
            @Argument AnnotationQueueReason reason
    ) {
        requireEvaluations();
        String email = RoleGuard.requireAuthenticated(authentication);
        return annotationQueueService.enqueue(taskId, reason, email);
    }

    @MutationMapping
    public AnnotationQueueItem completeAnnotation(
            Authentication authentication,
            @Argument UUID id,
            @Argument AnnotationQueueStatus status,
            @Argument String notes
    ) {
        requireEvaluations();
        String email = RoleGuard.requireAuthenticated(authentication);
        return annotationQueueService.complete(id, status, notes, email);
    }

    @MutationMapping
    public EvalDataset createEvalDataset(
            Authentication authentication,
            @Argument String name,
            @Argument String description,
            @Argument EvalDatasetSource source
    ) {
        requireExperiments();
        String email = RoleGuard.requireAuthenticated(authentication);
        return datasetService.create(name, description, source, email);
    }

    @MutationMapping
    public EvalDataset addTaskToEvalDataset(
            Authentication authentication,
            @Argument UUID datasetId,
            @Argument UUID taskId
    ) {
        requireExperiments();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return datasetService.addTask(datasetId, taskId);
    }

    @MutationMapping
    public EvalDataset populateEvalDatasetFromFailedEvals(
            Authentication authentication,
            @Argument UUID datasetId,
            @Argument Instant from,
            @Argument Instant to
    ) {
        requireExperiments();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return datasetService.addFailedEvalTasks(datasetId, from, to);
    }

    @MutationMapping
    public EvalDataset populateEvalDatasetFromFilter(
            Authentication authentication,
            @Argument UUID datasetId,
            @Argument SourceTool sourceTool,
            @Argument Integer minRetries,
            @Argument Instant from,
            @Argument Instant to
    ) {
        requireExperiments();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return datasetService.populateFromFilter(datasetId, sourceTool, minRetries, from, to);
    }

    @MutationMapping
    public EvalExperiment createEvalExperiment(
            Authentication authentication,
            @Argument String name,
            @Argument UUID datasetId,
            @Argument UUID baselineDefinitionId,
            @Argument UUID variantDefinitionId
    ) {
        requireExperiments();
        String email = RoleGuard.requireAuthenticated(authentication);
        return experimentService.create(name, datasetId, baselineDefinitionId, variantDefinitionId, email);
    }

    @MutationMapping
    public EvalExperiment runEvalExperiment(Authentication authentication, @Argument UUID experimentId) {
        requireExperiments();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return experimentService.run(experimentId);
    }

    @MutationMapping
    public PlaygroundEvalResult runPlaygroundEval(
            Authentication authentication,
            @Argument UUID taskId,
            @Argument UUID definitionId,
            @Argument String promptTemplate
    ) {
        requireEvaluations();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return playgroundService.run(taskId, definitionId, promptTemplate);
    }

    @MutationMapping
    public SessionFeedback submitSessionFeedback(
            Authentication authentication,
            @Argument UUID taskId,
            @Argument UUID eventId,
            @Argument int rating,
            @Argument String comment,
            @Argument FeedbackType feedbackType
    ) {
        requireEvaluations();
        String email = RoleGuard.requireAuthenticated(authentication);
        return sessionFeedbackService.submit(taskId, eventId, rating, comment, feedbackType, email);
    }

    @MutationMapping
    public List<TopicPattern> recomputeTopicPatterns(
            Authentication authentication,
            @Argument Integer windowDays
    ) {
        requirePatterns();
        RoleGuard.requireAdminOrSecadmin(authentication);
        return patternClusteringService.recomputeNow(windowDays);
    }
}
