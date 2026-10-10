package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortalSupportContent(
        @Size(max = 25) List<@NotNull @Valid Faq> faqs,
        @Email @Size(max = 180) String helpEmail,
        @Size(max = 80) @Pattern(regexp = "[+0-9() .-]*") String helpPhone,
        @Size(max = 240) String helpHours,
        @Size(max = 1200) @Pattern(regexp = "(?:https?://[^\\s]+)?") String helpUrl
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PortalSupportContent {
        faqs = faqs == null ? List.of() : List.copyOf(faqs);
        helpEmail = clean(helpEmail);
        helpPhone = clean(helpPhone);
        helpHours = clean(helpHours);
        helpUrl = clean(helpUrl);
    }

    public record Faq(@NotBlank @Size(max = 240) String question,
                      @NotBlank @Size(max = 3000) String answer) {
        public Faq {
            question = clean(question);
            answer = clean(answer);
        }
    }

    public static PortalSupportContent empty() {
        return new PortalSupportContent(null, null, null, null, null);
    }

    public static PortalSupportContent fromJson(String json) {
        if (json == null || json.isBlank()) return empty();
        try {
            return MAPPER.readValue(json, PortalSupportContent.class);
        } catch (Exception ignored) {
            return empty();
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not save portal FAQ and help details.", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
