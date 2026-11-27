/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.replay;

import com.agentretry.contract.SessionReplayFormat;
import com.agentretry.manager.replay.SessionReplayService.TaskSessionReplay;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskReplayController {

    private final SessionReplayService sessionReplayService;

    public TaskReplayController(SessionReplayService sessionReplayService) {
        this.sessionReplayService = sessionReplayService;
    }

    @GetMapping("/{taskId}/replay")
    public ResponseEntity<?> replay(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "JSON") SessionReplayFormat format
    ) {
        if (format == SessionReplayFormat.JSON) {
            TaskSessionReplay replay = sessionReplayService.buildReplay(taskId);
            return ResponseEntity.ok(replay);
        }

        String body = sessionReplayService.render(taskId, format);
        MediaType mediaType = format == SessionReplayFormat.PROMPT
                ? MediaType.TEXT_PLAIN
                : MediaType.TEXT_MARKDOWN;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"task-" + taskId + "-replay.txt\"")
                .contentType(mediaType)
                .body(body);
    }
}
