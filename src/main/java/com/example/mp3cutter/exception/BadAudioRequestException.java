package com.example.mp3cutter.exception;

public class BadAudioRequestException extends RuntimeException {

    public BadAudioRequestException(String message) {
        super(message);
    }
}
