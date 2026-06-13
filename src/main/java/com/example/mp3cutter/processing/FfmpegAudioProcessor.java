package com.example.mp3cutter.processing;

import com.example.mp3cutter.config.AudioStorageProperties;
import com.example.mp3cutter.exception.AudioProcessingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegAudioProcessor {

    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(2);

    private final AudioStorageProperties properties;

    public FfmpegAudioProcessor(AudioStorageProperties properties) {
        this.properties = properties;
    }

    public long durationMs(Path input) {
        List<String> command = List.of(
                properties.ffprobePath(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.toString()
        );
        ProcessResult result = run(command);
        if (result.exitCode() != 0) {
            throw new AudioProcessingException("Uploaded file is not a readable MP3");
        }
        try {
            double seconds = Double.parseDouble(result.output().trim());
            if (!Double.isFinite(seconds) || seconds <= 0) {
                throw new NumberFormatException("Non-positive duration");
            }
            return Math.round(seconds * 1000);
        } catch (NumberFormatException ex) {
            throw new AudioProcessingException("Unable to determine MP3 duration", ex);
        }
    }

    public Path trim(Path input, Path output, long startMs, long endMs) {
        double startSeconds = startMs / 1000.0;
        double endSeconds = endMs / 1000.0;

        List<String> copyCommand = new ArrayList<>();
        copyCommand.add(properties.ffmpegPath());
        copyCommand.add("-y");
        copyCommand.add("-hide_banner");
        copyCommand.add("-loglevel");
        copyCommand.add("error");
        copyCommand.add("-ss");
        copyCommand.add(formatSeconds(startSeconds));
        copyCommand.add("-to");
        copyCommand.add(formatSeconds(endSeconds));
        copyCommand.add("-i");
        copyCommand.add(input.toString());
        copyCommand.add("-map");
        copyCommand.add("0:a:0");
        copyCommand.add("-c");
        copyCommand.add("copy");
        copyCommand.add(output.toString());

        ProcessResult copyResult = run(copyCommand);
        if (copyResult.exitCode() == 0 && hasContent(output)) {
            return output;
        }

        deleteQuietly(output);
        List<String> encodeCommand = List.of(
                properties.ffmpegPath(),
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-ss", formatSeconds(startSeconds),
                "-to", formatSeconds(endSeconds),
                "-i", input.toString(),
                "-map", "0:a:0",
                "-codec:a", "libmp3lame",
                "-q:a", "2",
                output.toString()
        );
        ProcessResult encodeResult = run(encodeCommand);
        if (encodeResult.exitCode() != 0 || !hasContent(output)) {
            deleteQuietly(output);
            throw new AudioProcessingException("FFmpeg failed to trim the MP3: " + encodeResult.output());
        }
        return output;
    }

    private ProcessResult run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean completed = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new AudioProcessingException("Audio processing timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException ex) {
            throw new AudioProcessingException("Unable to start FFmpeg/FFprobe. Check that FFmpeg is installed and configured.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AudioProcessingException("Audio processing was interrupted", ex);
        }
    }

    private String formatSeconds(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private boolean hasContent(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
