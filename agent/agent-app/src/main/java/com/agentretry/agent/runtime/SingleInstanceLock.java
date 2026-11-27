/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.runtime;

import com.agentretry.agent.config.AgentProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ensures only one collector agent process uses the local buffer and identity store.
 */
@Component
@ConditionalOnProperty(name = "agent.cli.command", havingValue = "run", matchIfMissing = true)
public class SingleInstanceLock {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceLock.class);

    private final Path lockFile;
    private RandomAccessFile lockHandle;
    private FileLock lock;

    public SingleInstanceLock(AgentProperties properties) {
        String configured = properties.getIdentity().getLockFile();
        this.lockFile = Path.of(configured.replace("${user.home}", System.getProperty("user.home")));
        acquire();
    }

    private void acquire() {
        try {
            Files.createDirectories(lockFile.getParent());
            lockHandle = new RandomAccessFile(lockFile.toFile(), "rw");
            FileChannel channel = lockHandle.getChannel();
            lock = channel.tryLock();
            if (lock == null) {
                throw new IllegalStateException(
                        "Another RetrySight agent is already running (lock: " + lockFile + ")");
            }
            lockHandle.writeBytes(Long.toString(ProcessHandle.current().pid()) + "\n");
            log.info("Acquired single-instance lock {}", lockFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to acquire agent lock at " + lockFile, ex);
        }
    }

    public Path lockFile() {
        return lockFile;
    }

    public boolean isHeld() {
        return lock != null && lock.isValid();
    }

    @PreDestroy
    public void release() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ex) {
            log.debug("Failed to release lock: {}", ex.getMessage());
        } finally {
            lock = null;
            if (lockHandle != null) {
                try {
                    lockHandle.close();
                } catch (IOException ex) {
                    log.debug("Failed to close lock handle: {}", ex.getMessage());
                }
                lockHandle = null;
            }
        }
    }
}
