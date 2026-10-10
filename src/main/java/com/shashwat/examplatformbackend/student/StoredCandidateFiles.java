package com.shashwat.examplatformbackend.student;

public record StoredCandidateFiles(
        String photoPath,
        String signaturePath,
        String idCardPath,
        String class10MarksheetPath,
        String class12MarksheetPath,
        String ugDocumentPath,
        String pgDocumentPath
) {
}
