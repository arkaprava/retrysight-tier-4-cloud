/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.archive;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class S3ArchiveSink implements ArchiveSink, AutoCloseable {

    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public S3ArchiveSink(
            String bucket,
            String prefix,
            String region,
            String endpoint,
            String accessKey,
            String secretKey
    ) {
        this.bucket = bucket;
        this.prefix = normalizePrefix(prefix);
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region == null || region.isBlank() ? "us-east-1" : region));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);
        }
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        this.client = builder.build();
    }

    @Override
    public ArchiveDestinationType type() {
        return ArchiveDestinationType.S3;
    }

    @Override
    public long write(String relativePath, byte[] content) {
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key(relativePath)).build(),
                RequestBody.fromBytes(content)
        );
        return content.length;
    }

    @Override
    public long write(String relativePath, InputStream content, long contentLength) {
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key(relativePath)).build(),
                RequestBody.fromInputStream(content, contentLength)
        );
        return contentLength;
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key(relativePath)).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("404")) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public Optional<byte[]> read(String relativePath) {
        try {
            var bytes = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key(relativePath)).build());
            return Optional.of(bytes.asByteArray());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> list(String relativePrefix) {
        String listPrefix = key(relativePrefix == null ? "" : relativePrefix);
        List<String> keys = new ArrayList<>();
        var response = client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(listPrefix)
                .build());
        response.contents().forEach(obj -> {
            String full = obj.key();
            if (full.startsWith(prefix)) {
                keys.add(full.substring(prefix.length()));
            } else {
                keys.add(full);
            }
        });
        return keys;
    }

    @Override
    public void testConnection() throws Exception {
        String probe = "_probe/" + System.currentTimeMillis() + ".txt";
        write(probe, "ok".getBytes());
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(probe)).build());
    }

    @Override
    public void close() {
        client.close();
    }

    private String key(String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return prefix + normalized;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String value = prefix.replace('\\', '/');
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }
}
