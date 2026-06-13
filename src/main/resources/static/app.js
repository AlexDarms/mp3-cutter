import WaveSurfer from 'https://unpkg.com/wavesurfer.js@7/dist/wavesurfer.esm.js';
import RegionsPlugin from 'https://unpkg.com/wavesurfer.js@7/dist/plugins/regions.esm.js';

const elements = {
    fileInput: document.querySelector('#fileInput'),
    status: document.querySelector('#status'),
    waveform: document.querySelector('#waveform'),
    playPause: document.querySelector('#playPause'),
    preview: document.querySelector('#preview'),
    stopPreview: document.querySelector('#stopPreview'),
    save: document.querySelector('#save'),
    currentTime: document.querySelector('#currentTime'),
    duration: document.querySelector('#duration'),
    startInput: document.querySelector('#startInput'),
    endInput: document.querySelector('#endInput'),
    zoom: document.querySelector('#zoom'),
    selectionText: document.querySelector('#selectionText')
};

let fileId = null;
let durationSeconds = 0;
let waveSurfer = null;
let regions = null;
let activeRegion = null;
let previewTimer = null;

function setStatus(message, isError = false) {
    elements.status.textContent = message;
    elements.status.classList.toggle('error', isError);
}

function formatTime(seconds) {
    if (!Number.isFinite(seconds)) {
        return '00:00.000';
    }
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds - minutes * 60;
    return `${String(minutes).padStart(2, '0')}:${remaining.toFixed(3).padStart(6, '0')}`;
}

function setControlsEnabled(enabled) {
    elements.playPause.disabled = !enabled;
    elements.preview.disabled = !enabled;
    elements.save.disabled = !enabled;
    elements.startInput.disabled = !enabled;
    elements.endInput.disabled = !enabled;
    elements.zoom.disabled = !enabled;
}

function createWaveSurfer() {
    if (waveSurfer) {
        waveSurfer.destroy();
    }
    regions = RegionsPlugin.create();
    waveSurfer = WaveSurfer.create({
        container: elements.waveform,
        waveColor: '#8795a8',
        progressColor: '#0f7b6c',
        cursorColor: '#18202a',
        height: 220,
        minPxPerSec: Number(elements.zoom.value),
        normalize: true,
        plugins: [regions]
    });

    waveSurfer.on('ready', () => {
        durationSeconds = waveSurfer.getDuration();
        elements.duration.textContent = formatTime(durationSeconds);
        elements.currentTime.textContent = formatTime(0);
        createInitialRegion();
        setControlsEnabled(true);
        setStatus('Ready. Drag the selection edges or type timestamps.');
    });

    waveSurfer.on('timeupdate', (time) => {
        elements.currentTime.textContent = formatTime(time);
    });

    waveSurfer.on('play', () => {
        elements.playPause.textContent = 'Pause';
    });

    waveSurfer.on('pause', () => {
        elements.playPause.textContent = 'Play';
    });

    waveSurfer.on('finish', () => {
        elements.playPause.textContent = 'Play';
        elements.stopPreview.disabled = true;
        clearTimeout(previewTimer);
    });

    regions.on('region-updated', (region) => {
        activeRegion = region;
        syncInputsFromRegion();
    });
}

function createInitialRegion() {
    const end = Math.min(durationSeconds, Math.max(1, durationSeconds * 0.25));
    activeRegion = regions.addRegion({
        start: 0,
        end,
        color: 'rgba(15, 123, 108, 0.18)',
        drag: true,
        resize: true
    });
    syncInputsFromRegion();
}

function syncInputsFromRegion() {
    if (!activeRegion) {
        return;
    }
    elements.startInput.value = activeRegion.start.toFixed(3);
    elements.endInput.value = activeRegion.end.toFixed(3);
    elements.selectionText.textContent = `${formatTime(activeRegion.start)} - ${formatTime(activeRegion.end)}`;
}

function syncRegionFromInputs() {
    if (!activeRegion) {
        return;
    }
    const start = Math.max(0, Number(elements.startInput.value));
    const end = Math.min(durationSeconds, Number(elements.endInput.value));
    if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) {
        setStatus('Start must be before end and inside the track.', true);
        syncInputsFromRegion();
        return;
    }
    activeRegion.setOptions({ start, end });
    setStatus('Selection updated.');
}

async function uploadFile(file) {
    if (!file) {
        return;
    }
    if (!file.name.toLowerCase().endsWith('.mp3')) {
        setStatus('Please choose an .mp3 file.', true);
        return;
    }

    setControlsEnabled(false);
    setStatus('Uploading and reading MP3 metadata...');
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/audio/upload', {
        method: 'POST',
        body: formData
    });
    if (!response.ok) {
        throw new Error(await readError(response));
    }
    const payload = await response.json();
    fileId = payload.fileId;
    createWaveSurfer();
    try {
        await waveSurfer.load(`/api/audio/${fileId}/stream`);
    } catch (error) {
        throw new Error(`Upload succeeded, but the browser could not load the MP3 stream: ${error.message}`);
    }
}

async function saveTrimmedMp3() {
    if (!fileId || !activeRegion) {
        return;
    }
    const startMs = Math.round(activeRegion.start * 1000);
    const endMs = Math.round(activeRegion.end * 1000);
    setStatus('Trimming MP3...');
    elements.save.disabled = true;

    const response = await fetch('/api/audio/trim', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fileId, startMs, endMs })
    });
    elements.save.disabled = false;
    if (!response.ok) {
        throw new Error(await readError(response));
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'trimmed.mp3';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setStatus('Trimmed MP3 downloaded.');
}

function previewSelection() {
    if (!waveSurfer || !activeRegion) {
        return;
    }
    clearTimeout(previewTimer);
    waveSurfer.setTime(activeRegion.start);
    waveSurfer.play();
    elements.stopPreview.disabled = false;
    previewTimer = setTimeout(() => {
        waveSurfer.pause();
        elements.stopPreview.disabled = true;
    }, Math.max(0, activeRegion.end - activeRegion.start) * 1000);
}

function stopPreview() {
    clearTimeout(previewTimer);
    if (waveSurfer) {
        waveSurfer.pause();
    }
    elements.stopPreview.disabled = true;
}

async function readError(response) {
    try {
        const payload = await response.json();
        return payload.message || 'Request failed';
    } catch {
        return 'Request failed';
    }
}

elements.fileInput.addEventListener('change', async (event) => {
    try {
        await uploadFile(event.target.files[0]);
    } catch (error) {
        setStatus(error.message, true);
        setControlsEnabled(false);
    }
});

elements.playPause.addEventListener('click', () => {
    waveSurfer.playPause();
});

elements.preview.addEventListener('click', previewSelection);
elements.stopPreview.addEventListener('click', stopPreview);
elements.save.addEventListener('click', async () => {
    try {
        await saveTrimmedMp3();
    } catch (error) {
        setStatus(error.message, true);
    }
});

elements.startInput.addEventListener('change', syncRegionFromInputs);
elements.endInput.addEventListener('change', syncRegionFromInputs);
elements.zoom.addEventListener('input', () => {
    if (waveSurfer) {
        waveSurfer.zoom(Number(elements.zoom.value));
    }
});
