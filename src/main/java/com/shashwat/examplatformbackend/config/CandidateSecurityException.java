package com.shashwat.examplatformbackend.config;

public class CandidateSecurityException extends RuntimeException {
    public CandidateSecurityException() {
        super("Refresh the page and try again.");
    }
}
