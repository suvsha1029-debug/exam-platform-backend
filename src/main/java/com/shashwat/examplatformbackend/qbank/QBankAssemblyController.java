package com.shashwat.examplatformbackend.qbank;

import com.shashwat.examplatformbackend.qbank.QBankAssemblyService.DownloadedAssemblyFile;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAggregatedKeyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAnalyzeResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssembleRequest;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssemblyHistoryResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssemblyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankDefaultKeyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankPublishResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/exams/{examCode}/qbank")
public class QBankAssemblyController {
    private final QBankAssemblyService qbankAssemblyService;

    public QBankAssemblyController(QBankAssemblyService qbankAssemblyService) {
        this.qbankAssemblyService = qbankAssemblyService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QBankAnalyzeResponse analyze(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return qbankAssemblyService.analyze(examCode, file);
    }

    @PostMapping(value = "/default-answer-key", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QBankDefaultKeyResponse defaultAnswerKey(@PathVariable String examCode, @RequestParam("file") MultipartFile file) {
        return qbankAssemblyService.storeDefaultAnswerKey(examCode, file);
    }

    @PostMapping("/assemble")
    public QBankAssemblyResponse assemble(@PathVariable String examCode, @RequestBody QBankAssembleRequest request) {
        return qbankAssemblyService.assemble(examCode, request);
    }

    @PostMapping("/aggregate-answer-key")
    public QBankAggregatedKeyResponse aggregateAnswerKey(@PathVariable String examCode) {
        return qbankAssemblyService.aggregatePublishedAnswerKey(examCode);
    }

    @PostMapping("/reconstruct-evaluation-key")
    public QBankAggregatedKeyResponse reconstructEvaluationKey(@PathVariable String examCode) {
        return qbankAssemblyService.reconstructPublishedAnswerKey(examCode);
    }

    @GetMapping("/assemblies")
    public List<QBankAssemblyHistoryResponse> assemblies(@PathVariable String examCode) {
        return qbankAssemblyService.listAssemblies(examCode);
    }

    @PostMapping("/assemblies/{assemblyId}/publish")
    public QBankPublishResponse publish(@PathVariable String examCode, @PathVariable String assemblyId) {
        return qbankAssemblyService.publish(examCode, assemblyId);
    }

    @GetMapping(value = "/assemblies/{assemblyId}/question-paper", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> questionPaper(@PathVariable String examCode, @PathVariable String assemblyId) {
        return download(qbankAssemblyService.downloadAssemblyFile(examCode, assemblyId, "question-paper"));
    }

    @GetMapping(value = "/assemblies/{assemblyId}/answer-key", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> answerKey(@PathVariable String examCode, @PathVariable String assemblyId) {
        return download(qbankAssemblyService.downloadAssemblyFile(examCode, assemblyId, "answer-key"));
    }

    @GetMapping(value = "/assemblies/{assemblyId}/question-paper.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> questionPaperPdf(@PathVariable String examCode, @PathVariable String assemblyId) {
        return downloadPdf(qbankAssemblyService.downloadAssemblyPdf(examCode, assemblyId, "question-paper"));
    }

    @GetMapping(value = "/assemblies/{assemblyId}/answer-key.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> answerKeyPdf(@PathVariable String examCode, @PathVariable String assemblyId) {
        return downloadPdf(qbankAssemblyService.downloadAssemblyPdf(examCode, assemblyId, "answer-key"));
    }

    private static ResponseEntity<byte[]> download(DownloadedAssemblyFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
                .body(file.bytes());
    }

    private static ResponseEntity<byte[]> downloadPdf(DownloadedAssemblyFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(file.bytes());
    }
}
