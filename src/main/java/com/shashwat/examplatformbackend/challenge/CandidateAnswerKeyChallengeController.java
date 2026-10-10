package com.shashwat.examplatformbackend.challenge;

import com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.CandidateChallengeAssetResponse;
import com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.CandidateChallengeResponse;
import com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.CandidateChallengeReviewRequest;
import com.shashwat.examplatformbackend.challenge.CandidateAnswerKeyChallengeDtos.ChallengeQuestionSummary;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class CandidateAnswerKeyChallengeController {
    private final CandidateAnswerKeyChallengeService service;

    public CandidateAnswerKeyChallengeController(CandidateAnswerKeyChallengeService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/candidate/answer-key-challenges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateChallengeResponse submit(@RequestParam String registrationId,
                                             @RequestParam String questionId,
                                             @RequestParam(required = false) String displayQuestionId,
                                             @RequestParam(required = false) String questionNumber,
                                             @RequestParam(required = false) String noteText,
                                             @RequestParam("proofPdf") MultipartFile proofPdf,
                                             @RequestParam(value = "proofImages", required = false) List<MultipartFile> proofImages,
                                             HttpSession session) {
        String sessionRegistrationId = candidateSessionRegistrationId(session);
        if (!sessionRegistrationId.equals(registrationId == null ? "" : registrationId.trim())) {
            throw new IllegalStateException("Candidate session does not match this application number. Log in again.");
        }
        return service.submit(sessionRegistrationId, questionId, displayQuestionId, questionNumber, noteText, proofPdf, proofImages);
    }

    @PostMapping("/api/candidate/answer-key-challenges/history")
    public List<CandidateChallengeResponse> history(@RequestParam String registrationId, HttpSession session) {
        String sessionRegistrationId = candidateSessionRegistrationId(session);
        if (!sessionRegistrationId.equals(registrationId == null ? "" : registrationId.trim())) {
            throw new IllegalStateException("Candidate session does not match this application number. Log in again.");
        }
        return service.candidateHistory(sessionRegistrationId);
    }

    @GetMapping("/api/evaluation/{examCode}/challenges")
    public List<CandidateChallengeResponse> examChallenges(@PathVariable String examCode) {
        return service.examChallenges(examCode);
    }

    @GetMapping("/api/evaluation/{examCode}/challenge-summary")
    public List<ChallengeQuestionSummary> challengeSummary(@PathVariable String examCode) {
        return service.challengeSummary(examCode);
    }

    @GetMapping("/api/evaluation/challenges/{id}")
    public CandidateChallengeAssetResponse challengeAssets(@PathVariable Long id) {
        return service.challengeAssets(id);
    }

    @GetMapping("/api/evaluation/challenges/{id}/proof-pdf")
    public ResponseEntity<byte[]> proofPdf(@PathVariable Long id) {
        return inlineAsset(service.proofPdf(id));
    }

    @GetMapping("/api/evaluation/challenges/{id}/proof-images/{index}")
    public ResponseEntity<byte[]> proofImage(@PathVariable Long id, @PathVariable int index) {
        return inlineAsset(service.proofImage(id, index));
    }

    @GetMapping("/api/evaluation/challenges/{id}/question-image")
    public ResponseEntity<byte[]> questionImage(@PathVariable Long id) {
        return inlineImage(service.questionImage(id));
    }

    @GetMapping("/api/evaluation/challenges/{id}/option-images/{optionLetter}")
    public ResponseEntity<byte[]> optionImage(@PathVariable Long id, @PathVariable String optionLetter) {
        return inlineImage(service.optionImage(id, optionLetter));
    }

    @PatchMapping("/api/evaluation/challenges/{id}/accept")
    public CandidateChallengeResponse accept(@PathVariable Long id, @RequestBody(required = false) CandidateChallengeReviewRequest request) {
        return service.accept(id, "exam-admin", request == null ? null : request.reviewNote());
    }

    @PatchMapping("/api/evaluation/challenges/{id}/reject")
    public CandidateChallengeResponse reject(@PathVariable Long id, @RequestBody(required = false) CandidateChallengeReviewRequest request) {
        return service.reject(id, "exam-admin", request == null ? null : request.reviewNote());
    }

    @PatchMapping("/api/evaluation/challenges/{id}/resolve")
    public CandidateChallengeResponse resolve(@PathVariable Long id, @RequestBody CandidateChallengeReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Choose a challenge resolution.");
        }
        return service.resolve(id, "exam-admin", request.resolution(), request.reviewNote());
    }

    private static String candidateSessionRegistrationId(HttpSession session) {
        Object value = session.getAttribute("candidateRegistrationId");
        if (value instanceof String registrationId && !registrationId.isBlank()) {
            return registrationId;
        }
        throw new IllegalStateException("Candidate session is not active. Log in again.");
    }

    private static ResponseEntity<byte[]> download(CandidateAnswerKeyChallengeService.ChallengeAssetDownload asset) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(asset.fileName()).build().toString())
                .body(asset.bytes());
    }

    private static ResponseEntity<byte[]> inlineImage(CandidateAnswerKeyChallengeService.ChallengeAssetDownload asset) {
        return inlineAsset(asset);
    }

    private static ResponseEntity<byte[]> inlineAsset(CandidateAnswerKeyChallengeService.ChallengeAssetDownload asset) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(asset.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(asset.bytes());
    }
}
