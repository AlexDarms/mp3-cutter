package com.example.mp3cutter.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.audio")
public record AudioStorageProperties(
        @Positive long maxUploadBytes,
        Duration tempFileTimeout,
        @NotBlank String ffmpegPath,
        @NotBlank String ffprobePath
) {
}
