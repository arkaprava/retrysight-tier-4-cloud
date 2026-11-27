/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.config.EvalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class OnlineEvalDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OnlineEvalDispatcher.class);

    private final EvalRunnerService evalRunnerService;
    private final EvalProperties evalProperties;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "online-eval");
        t.setDaemon(true);
        return t;
    });

    public OnlineEvalDispatcher(EvalRunnerService evalRunnerService, EvalProperties evalProperties) {
        this.evalRunnerService = evalRunnerService;
        this.evalProperties = evalProperties;
    }

    public void scheduleAfterTaskComplete(UUID taskId) {
        if (!evalRunnerService.isEnabled() || !evalProperties.isOnlineOnComplete()) {
            return;
        }
        runAfterCommit(() -> executor.execute(() -> {
            try {
                evalRunnerService.runOnlineEvaluations(taskId);
            } catch (Exception ex) {
                log.warn("Online eval failed for task {}: {}", taskId, ex.getMessage());
            }
        }));
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
