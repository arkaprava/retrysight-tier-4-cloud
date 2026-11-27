/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class FileSystemArchiveSink implements ArchiveSink {

    private final ArchiveDestinationType type;
    private final Path root;

    public FileSystemArchiveSink(ArchiveDestinationType type, Path root) {
        this.type = type;
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public ArchiveDestinationType type() {
        return type;
    }

    @Override
    public long write(String relativePath, byte[] content) throws Exception {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return content.length;
    }

    @Override
    public long write(String relativePath, InputStream content, long contentLength) throws Exception {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        try (var out = Files.newOutputStream(target)) {
            return content.transferTo(out);
        }
    }

    @Override
    public boolean exists(String relativePath) throws Exception {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public Optional<byte[]> read(String relativePath) throws Exception {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(target));
    }

    @Override
    public List<String> list(String relativePrefix) throws Exception {
        Path base = resolve(relativePrefix == null ? "" : relativePrefix);
        if (!Files.exists(base)) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> results.add(root.relativize(path).toString().replace('\\', '/')));
        }
        return results;
    }

    @Override
    public void testConnection() throws Exception {
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        if (!Files.isWritable(root)) {
            throw new IOException("Archive root is not writable: " + root);
        }
        String probe = "_probe/" + System.currentTimeMillis() + ".txt";
        write(probe, "ok".getBytes());
        Files.deleteIfExists(resolve(probe));
    }

    public Path root() {
        return root;
    }

    private Path resolve(String relativePath) throws IOException {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path escapes archive root: " + relativePath);
        }
        return resolved;
    }
}
