package com.example.mp3cutter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TrimRequest(
        @NotBlank String fileId,
        @NotNull @PositiveOrZero Long startMs,
        @NotNull @PositiveOrZero Long endMs
) {
}
