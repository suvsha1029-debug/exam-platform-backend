package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record PortalNotice(@NotBlank @Size(max = 500) String text, boolean isNew, String publishedOn) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public PortalNotice {
        text = text == null ? "" : text.trim();
        publishedOn = publishedOn == null ? "" : publishedOn.trim();
        if (!publishedOn.isEmpty()) {
            if (!publishedOn.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Notice date must use YYYY-MM-DD.");
            try { LocalDate.parse(publishedOn); }
            catch (java.time.DateTimeException error) { throw new IllegalArgumentException("Enter a valid notice date."); }
        }
    }
    public static List<PortalNotice> ordered(List<PortalNotice> notices) {
        return notices.stream().sorted(Comparator.comparing(PortalNotice::isNew).reversed()
                .thenComparing(PortalNotice::publishedOn, Comparator.reverseOrder())).toList();
    }
    public static List<PortalNotice> parse(String json) {
        if (json == null) return null;
        try { return ordered(MAPPER.readValue(json, new TypeReference<List<PortalNotice>>() {})); }
        catch (Exception ignored) { return null; }
    }
    public static String encode(List<PortalNotice> notices) {
        try { return MAPPER.writeValueAsString(ordered(notices)); }
        catch (Exception error) { throw new IllegalArgumentException("Could not save notices.", error); }
    }
}
