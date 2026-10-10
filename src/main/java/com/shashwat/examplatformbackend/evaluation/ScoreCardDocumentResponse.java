package com.shashwat.examplatformbackend.evaluation;

import java.util.List;

public record ScoreCardDocumentResponse(String fileName, String pdfDataUrl, List<String> pages,
                                        String candidateName, String applicationNumber) {
}
