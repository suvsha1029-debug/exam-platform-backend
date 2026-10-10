package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ScoreCardAppearance(
        @Size(max = 180) String instituteName,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String backgroundColor,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String accentColor,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String stripColor,
        Boolean stripsEnabled,
        @Min(2) @Max(15) Integer watermarkOpacity,
        @Size(max = 100) String notesTitle,
        @Size(max = 6000) String notesText,
        @Size(max = 3000000) String notesImageDataUrl
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ScoreCardAppearance {
        instituteName = instituteName == null ? "" : instituteName.trim();
        backgroundColor = backgroundColor == null ? "#ffffff" : backgroundColor;
        accentColor = accentColor == null ? "#153e50" : accentColor;
        stripColor = stripColor == null ? "#967344" : stripColor;
        stripsEnabled = stripsEnabled == null || stripsEnabled;
        watermarkOpacity = watermarkOpacity == null ? 6 : watermarkOpacity;
        notesTitle = notesTitle == null ? "Score information" : notesTitle.trim();
        notesText = notesText == null ? "" : notesText.trim();
        notesImageDataUrl = notesImageDataUrl == null ? "" : notesImageDataUrl;
    }

    public static ScoreCardAppearance defaults() {
        return new ScoreCardAppearance(null, null, null, null, null, null, null, null, null);
    }

    public static ScoreCardAppearance fromJson(String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            return MAPPER.readValue(json, ScoreCardAppearance.class);
        } catch (Exception ignored) {
            return defaults();
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not save score-card appearance.", exception);
        }
    }
}
