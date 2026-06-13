package com.example.mp3cutter.dto;

public record AudioMetadataResponse(
        String fileId,
        long durationMs,
        String originalFilename,
        long sizeBytes
) {
}
