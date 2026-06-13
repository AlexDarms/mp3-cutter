package com.example.mp3cutter.controller;

import com.example.mp3cutter.dto.AudioMetadataResponse;
import com.example.mp3cutter.dto.TrimRequest;
import com.example.mp3cutter.dto.UploadResponse;
import com.example.mp3cutter.model.StoredAudioFile;
import com.example.mp3cutter.service.AudioService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audioService;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return audioService.upload(file);
    }

    @GetMapping("/{fileId}/metadata")
    public AudioMetadataResponse metadata(@PathVariable String fileId) {
        StoredAudioFile file = audioService.requireFile(fileId);
        return new AudioMetadataResponse(file.id(), file.durationMs(), file.originalFilename(), file.sizeBytes());
    }

    @GetMapping("/{fileId}/stream")
    public ResponseEntity<Resource> streamOriginal(@PathVariable String fileId) {
        StoredAudioFile file = audioService.requireFile(fileId);
        FileSystemResource resource = new FileSystemResource(file.path());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalFilename())
                        .build()
                        .toString())
                .body(resource);
    }

    @PostMapping(value = "/trim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> trim(@Valid @RequestBody TrimRequest request) {
        Path trimmed = audioService.trim(request);
        String filename = "trimmed-" + request.fileId() + ".mp3";

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(trimmed)) {
                inputStream.transferTo(outputStream);
            } finally {
                audioService.deleteGeneratedFile(trimmed);
            }
        };

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("audio/mpeg"))
                    .contentLength(Files.size(trimmed))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename)
                            .build()
                            .toString())
                    .body(body);
        } catch (IOException ex) {
            audioService.deleteGeneratedFile(trimmed);
            throw new IllegalStateException("Unable to read generated MP3", ex);
        }
    }
}
