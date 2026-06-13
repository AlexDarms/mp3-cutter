# MP3 Cutter

A complete Spring Boot MVP for uploading, previewing, selecting, trimming, and downloading MP3 clips. The backend stores files only in the operating-system temp directory and removes generated downloads immediately after streaming.

## Implementation Plan

1. Create a Java 21 / Spring Boot 3 Maven project.
2. Add REST endpoints for upload, metadata, original audio streaming, and trimming.
3. Manage uploads in an in-memory registry backed by OS temporary files.
4. Use FFprobe for duration validation and FFmpeg for trimming.
5. Add scheduled cleanup, shutdown cleanup, and generated-file cleanup after download.
6. Build a static HTML/CSS/vanilla JS UI using WaveSurfer.js regions for waveform, playback, zoom, markers, preview, and download.

## Requirements

- Java 21
- Maven 3.9+
- FFmpeg and FFprobe available on `PATH`
- Browser with internet access for the WaveSurfer.js CDN module

## Run

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## FFmpeg Installation

### Windows

Install with Winget:

```powershell
winget install Gyan.FFmpeg
```

Then open a new terminal and verify:

```powershell
ffmpeg -version
ffprobe -version
```

### macOS

```bash
brew install ffmpeg
```

### Ubuntu/Debian

```bash
sudo apt update
sudo apt install ffmpeg
```

If FFmpeg is not on `PATH`, set paths in `src/main/resources/application.yml`:

```yaml
app:
  audio:
    ffmpeg-path: C:/ffmpeg/bin/ffmpeg.exe
    ffprobe-path: C:/ffmpeg/bin/ffprobe.exe
```

## Configuration

`src/main/resources/application.yml` contains:

- `server.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`
- `app.audio.max-upload-bytes`
- `app.audio.temp-file-timeout`
- `app.audio.cleanup-interval-ms`
- `app.audio.ffmpeg-path`
- `app.audio.ffprobe-path`

## API

### Upload

```bash
curl -F "file=@song.mp3" http://localhost:8080/api/audio/upload
```

Response:

```json
{
  "fileId": "..."
}
```

### Metadata

```bash
curl http://localhost:8080/api/audio/{fileId}/metadata
```

Response:

```json
{
  "fileId": "...",
  "durationMs": 123456,
  "originalFilename": "song.mp3",
  "sizeBytes": 3456789
}
```

### Stream Original

```bash
curl -L http://localhost:8080/api/audio/{fileId}/stream --output original.mp3
```

### Trim

```bash
curl -X POST http://localhost:8080/api/audio/trim \
  -H "Content-Type: application/json" \
  -d "{\"fileId\":\"{fileId}\",\"startMs\":12000,\"endMs\":45000}" \
  --output trimmed.mp3
```

## Temporary File Policy

- No database is used.
- Uploads are stored under an application-owned OS temp directory.
- Generated trimmed files are deleted after the response stream completes.
- Expired uploads are removed by scheduled cleanup.
- Remaining temporary files are removed on application shutdown when possible.

## Notes

The trim operation first attempts FFmpeg stream copy (`-c copy`) to preserve MP3 quality. If FFmpeg cannot produce a valid clip that way, the backend falls back to high-quality MP3 encoding with `libmp3lame -q:a 2`.
