package com.example.mp3cutter.service;

import com.example.mp3cutter.config.AudioStorageProperties;
import com.example.mp3cutter.dto.TrimRequest;
import com.example.mp3cutter.dto.UploadResponse;
import com.example.mp3cutter.exception.BadAudioRequestException;
import com.example.mp3cutter.model.StoredAudioFile;
import com.example.mp3cutter.processing.FfmpegAudioProcessor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class AudioService {

    private final AudioStorageProperties properties;
    private final FfmpegAudioProcessor audioProcessor;
    private final TemporaryFileService temporaryFileService;

    public AudioService(
            AudioStorageProperties properties,
            FfmpegAudioProcessor audioProcessor,
            TemporaryFileService temporaryFileService
    ) {
        this.properties = properties;
        this.audioProcessor = audioProcessor;
        this.temporaryFileService = temporaryFileService;
    }

    public UploadResponse upload(MultipartFile file) {
        validateUpload(file);

        String id = UUID.randomUUID().toString();
        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        Path target = temporaryFileService.newUploadPath(id);
        try {
            file.transferTo(target);
            long durationMs = audioProcessor.durationMs(target);
            temporaryFileService.registerUpload(id, target, safeFilename, file.getSize(), durationMs);
            return new UploadResponse(id);
        } catch (IOException ex) {
            temporaryFileService.deleteQuietly(target);
            throw new BadAudioRequestException("Unable to store uploaded file");
        } catch (RuntimeException ex) {
            temporaryFileService.deleteQuietly(target);
            throw ex;
        }
    }

    public StoredAudioFile requireFile(String fileId) {
        return temporaryFileService.require(fileId);
    }

    public Path trim(TrimRequest request) {
        StoredAudioFile file = requireFile(request.fileId());
        validateTrim(request, file);
        Path output = temporaryFileService.newGeneratedPath(request.fileId());
        return audioProcessor.trim(file.path(), output, request.startMs(), request.endMs());
    }

    public void deleteGeneratedFile(Path path) {
        temporaryFileService.deleteQuietly(path);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadAudioRequestException("An MP3 file is required");
        }
        if (file.getSize() > properties.maxUploadBytes()) {
            throw new BadAudioRequestException("File is larger than the configured upload limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            throw new BadAudioRequestException("Only .mp3 files are supported");
        }
    }

    private void validateTrim(TrimRequest request, StoredAudioFile file) {
        long startMs = request.startMs();
        long endMs = request.endMs();
        if (startMs >= endMs) {
            throw new BadAudioRequestException("Start time must be before end time");
        }
        if (endMs > file.durationMs()) {
            throw new BadAudioRequestException("End time cannot be beyond audio duration");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.mp3";
        }
        return Path.of(filename).getFileName().toString();
    }
}
