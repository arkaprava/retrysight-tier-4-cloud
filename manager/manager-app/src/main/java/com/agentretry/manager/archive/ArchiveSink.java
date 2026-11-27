/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.archive;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface ArchiveSink {

    ArchiveDestinationType type();

    /** Writes bytes to relative path under the archive root; returns bytes written. */
    long write(String relativePath, byte[] content) throws Exception;

    /** Writes a stream (for large parts). */
    long write(String relativePath, InputStream content, long contentLength) throws Exception;

    boolean exists(String relativePath) throws Exception;

    Optional<byte[]> read(String relativePath) throws Exception;

    List<String> list(String relativePrefix) throws Exception;

    /** Connectivity probe — write and delete a tiny object. */
    void testConnection() throws Exception;
}
