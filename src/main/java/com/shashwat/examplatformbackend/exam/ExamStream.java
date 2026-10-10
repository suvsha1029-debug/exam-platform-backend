package com.shashwat.examplatformbackend.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ExamStream {
    @Column(name = "short_code", nullable = false, length = 24)
    private String shortCode;

    @Column(name = "full_name", nullable = false, length = 140)
    private String fullName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected ExamStream() {
    }

    public ExamStream(String shortCode, String fullName, boolean enabled) {
        this.shortCode = shortCode;
        this.fullName = fullName;
        this.enabled = enabled;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
