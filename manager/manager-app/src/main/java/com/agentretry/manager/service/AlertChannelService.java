/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.AlertProperties;
import com.agentretry.manager.domain.AlertChannelEntity;
import com.agentretry.manager.graphql.AlertChannel;
import com.agentretry.manager.repository.AlertChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertChannelService {
    private static final Logger log = LoggerFactory.getLogger(AlertChannelService.class);

    private final AlertChannelRepository repository;
    private final AlertProperties alertProperties;
    private final RestClient restClient = RestClient.create();

    public AlertChannelService(AlertChannelRepository repository, AlertProperties alertProperties) {
        this.repository = repository;
        this.alertProperties = alertProperties;
    }

    @Transactional(readOnly = true)
    public List<AlertChannel> list() {
        return repository.findAll().stream().map(this::toGraphql).toList();
    }

    @Transactional
    public AlertChannel create(String name, String channelType, String target) {
        if (name == null || name.isBlank() || target == null || target.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and target are required");
        }
        String type = channelType == null ? "WEBHOOK" : channelType.trim().toUpperCase();
        if (!type.equals("WEBHOOK") && !type.equals("EMAIL") && !type.equals("SLACK")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelType must be WEBHOOK, EMAIL, or SLACK");
        }
        Instant now = Instant.now();
        AlertChannelEntity entity = new AlertChannelEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name.trim());
        entity.setChannelType(type);
        entity.setTarget(target.trim());
        entity.setActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        return toGraphql(entity);
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert channel not found");
        }
        repository.deleteById(id);
        return true;
    }

    public void notifyChannels(String message, Map<String, Object> payload) {
        for (AlertChannelEntity channel : repository.findByActiveTrue()) {
            try {
                if ("WEBHOOK".equals(channel.getChannelType()) || "SLACK".equals(channel.getChannelType())) {
                    if (!alertProperties.isWebhookEnabled()) {
                        continue;
                    }
                    restClient.post()
                            .uri(channel.getTarget())
                            .body(Map.of("text", message, "payload", payload))
                            .retrieve()
                            .toBodilessEntity();
                } else if ("EMAIL".equals(channel.getChannelType())) {
                    log.info("EMAIL alert to {}: {}", channel.getTarget(), message);
                }
            } catch (Exception ex) {
                log.warn("Failed to notify channel {}: {}", channel.getId(), ex.getMessage());
            }
        }
    }

    private AlertChannel toGraphql(AlertChannelEntity entity) {
        return new AlertChannel(
                entity.getId(),
                entity.getName(),
                entity.getChannelType(),
                entity.getTarget(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
