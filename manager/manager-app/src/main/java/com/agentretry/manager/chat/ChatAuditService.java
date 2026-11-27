/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatAuditService {
    private static final Logger log = LoggerFactory.getLogger(ChatAuditService.class);
    private static final int MAX = 200;
    private final List<ChatDtos.ChatAuditEntry> recent = Collections.synchronizedList(new ArrayList<>());

    public void record(String principal, String model, String mode, List<String> tools, int promptChars, String path) {
        ChatDtos.ChatAuditEntry entry = new ChatDtos.ChatAuditEntry(
                Instant.now().toString(),
                principal,
                model,
                mode,
                tools == null ? List.of() : List.copyOf(tools),
                promptChars,
                path
        );
        synchronized (recent) {
            recent.add(0, entry);
            while (recent.size() > MAX) {
                recent.remove(recent.size() - 1);
            }
        }
        log.info("chat audit principal={} mode={} model={} tools={} path={} promptChars={}",
                principal, mode, model, tools, path, promptChars);
    }

    public List<ChatDtos.ChatAuditEntry> recent(int limit) {
        synchronized (recent) {
            return List.copyOf(recent.subList(0, Math.min(limit, recent.size())));
        }
    }
}
