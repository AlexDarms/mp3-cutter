package com.example.mp3cutter.service;

import com.example.mp3cutter.config.AudioStorageProperties;
import com.example.mp3cutter.exception.FileNotFoundException;
import com.example.mp3cutter.model.StoredAudioFile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemporaryFileService {

    private final AudioStorageProperties properties;
    private final Map<String, StoredAudioFile> files = new ConcurrentHashMap<>();
    private Path tempRoot;

    public TemporaryFileService(AudioStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws IOException {
        tempRoot = Files.createTempDirectory("mp3-cutter-");
    }

    public Path newUploadPath(String id) {
        return tempRoot.resolve(id + ".mp3");
    }

    public Path newGeneratedPath(String fileId) {
        return tempRoot.resolve(fileId + "-trimmed-" + UUID.randomUUID() + ".mp3");
    }

    public void registerUpload(String id, Path path, String originalFilename, long sizeBytes, long durationMs) {
        Instant now = Instant.now();
        files.put(id, new StoredAudioFile(id, path, originalFilename, sizeBytes, durationMs, now, now));
    }

    public StoredAudioFile require(String id) {
        StoredAudioFile file = files.get(id);
        if (file == null || !Files.exists(file.path())) {
            files.remove(id);
            throw new FileNotFoundException("Temporary file was not found or has expired");
        }
        StoredAudioFile touched = file.touch(Instant.now());
        files.put(id, touched);
        return touched;
    }

    @Scheduled(fixedDelayString = "${app.audio.cleanup-interval-ms:300000}")
    public void cleanupExpiredUploads() {
        Instant cutoff = Instant.now().minus(properties.tempFileTimeout());
        for (StoredAudioFile file : files.values()) {
            if (file.lastAccessedAt().isBefore(cutoff)) {
                files.remove(file.id());
                deleteQuietly(file.path());
            }
        }
    }

    @PreDestroy
    public void cleanupOnShutdown() {
        for (StoredAudioFile file : files.values()) {
            deleteQuietly(file.path());
        }
        files.clear();
        try {
            if (tempRoot != null && Files.exists(tempRoot)) {
                try (var paths = Files.list(tempRoot)) {
                    paths.forEach(this::deleteQuietly);
                }
                Files.deleteIfExists(tempRoot);
            }
        } catch (IOException ignored) {
        }
    }

    public void deleteQuietly(Path path) {
        try {
            if (path != null && tempRoot != null && path.normalize().startsWith(tempRoot.normalize())) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }
}
