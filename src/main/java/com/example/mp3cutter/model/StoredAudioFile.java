package com.example.mp3cutter.model;

import java.nio.file.Path;
import java.time.Instant;

public record StoredAudioFile(
        String id,
        Path path,
        String originalFilename,
        long sizeBytes,
        long durationMs,
        Instant createdAt,
        Instant lastAccessedAt
) {
    public StoredAudioFile touch(Instant now) {
        return new StoredAudioFile(id, path, originalFilename, sizeBytes, durationMs, createdAt, now);
    }
}
