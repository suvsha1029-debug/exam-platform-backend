package com.shashwat.examplatformbackend.qbank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shashwat.examplatformbackend.common.JsonMapperFactory;
import com.shashwat.examplatformbackend.evaluation.AnswerKeyStorageService;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.qbank.QBankDtos.BlueprintTopicRequest;
import com.shashwat.examplatformbackend.qbank.QBankDtos.DifficultyInventory;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAggregatedKeyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAnalyzeResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssembleRequest;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssemblyHistoryResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankAssemblyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankDefaultKeyResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.QBankPublishResponse;
import com.shashwat.examplatformbackend.qbank.QBankDtos.SelectedQuestionSummary;
import com.shashwat.examplatformbackend.qbank.QBankDtos.TopicInventory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QBankAssemblyService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_TOPIC = "Unassigned Topic";
    private static final String DEFAULT_DIFFICULTY = "Unspecified";

    private final Path qbankRoot;
    private final Path assemblyRoot;
    private final ObjectMapper objectMapper;
    private final ExamRepository examRepository;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final AnswerKeyStorageService answerKeyStorageService;
    private final QBankAssemblyRepository assemblyRepository;
    private final QBankPdfExportService qbankPdfExportService;

    public QBankAssemblyService(
            @Value("${app.upload.dir}") String uploadDir,
            ExamRepository examRepository,
            QuestionPaperStorageService questionPaperStorageService,
            AnswerKeyStorageService answerKeyStorageService,
            QBankAssemblyRepository assemblyRepository,
            QBankPdfExportService qbankPdfExportService
    ) {
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.qbankRoot = uploadRoot.resolve("qbanks").normalize();
        this.assemblyRoot = uploadRoot.resolve("qbank-assemblies").normalize();
        this.objectMapper = JsonMapperFactory.createLargeJsonMapper();
        this.examRepository = examRepository;
        this.questionPaperStorageService = questionPaperStorageService;
        this.answerKeyStorageService = answerKeyStorageService;
        this.assemblyRepository = assemblyRepository;
        this.qbankPdfExportService = qbankPdfExportService;
    }

    public QBankAnalyzeResponse analyze(String examCode, MultipartFile file) {
        exam(examCode);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a qbank JSON file first.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "qbank.json" : file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Qbank upload must be a .json file.");
        }
        try {
            JsonNode root;
            try (InputStream input = file.getInputStream()) {
                root = objectMapper.readTree(input);
            }
            List<QBankQuestion> questions = normalizeQuestions(root);
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("Qbank JSON must contain at least one question.");
            }
            String qbankId = compactId();
            Path folder = qbankFolder(examCode);
            Files.createDirectories(folder);
            Path stored = folder.resolve(qbankId + ".json").normalize();
            guardInside(stored, qbankRoot);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, stored, StandardCopyOption.REPLACE_EXISTING);
            }
            return inventoryResponse(examCode, qbankId, originalName, questions);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read or store qbank JSON.");
        }
    }

    public QBankDefaultKeyResponse storeDefaultAnswerKey(String examCode, MultipartFile file) {
        exam(examCode);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload the full-bank answer key JSON first.");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "answer-key.json" : file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Answer key upload must be a .json file.");
        }
        try {
            JsonNode key;
            try (InputStream input = file.getInputStream()) {
                key = objectMapper.readTree(input);
            }
            ArrayNode answers = answersArray(key);
            if (answers == null || answers.isEmpty()) {
                throw new IllegalArgumentException("Answer key JSON must contain a non-empty answers array.");
            }
            Path folder = qbankFolder(examCode);
            Files.createDirectories(folder);
            Path stored = folder.resolve("default-answer-key.json").normalize();
            guardInside(stored, qbankRoot);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stored.toFile(), key);
            return new QBankDefaultKeyResponse(examCode, originalName, answers.size(), round(totalAnswerMarks(answers)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read or store full-bank answer key JSON.");
        }
    }

    @Transactional
    public QBankAssemblyResponse assemble(String examCode, QBankAssembleRequest request) {
        Exam exam = exam(examCode);
        if (request == null || blank(request.qbankId())) {
            throw new IllegalArgumentException("Analyze/upload a qbank before assembling a paper.");
        }
        JsonNode qbank = readJson(qbankPath(examCode, request.qbankId()), qbankRoot, "Could not load stored qbank JSON.");
        List<QBankQuestion> questions = normalizeQuestions(qbank);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Stored qbank has no selectable questions.");
        }
        List<QBankQuestion> selected = selectQuestions(questions, request);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Blueprint selected zero questions. Enter at least one difficulty count.");
        }
        String assemblyId = compactId();
        PaperAuthMetadata auth = newPaperAuth(sourcePaperLicense(qbank));
        List<ExportQuestion> exportQuestions = buildExportQuestions(selected, request);
        ObjectNode paper = buildSelectedPaper(qbank, exportQuestions, exam, request, auth, assemblyId);
        Path folder = assemblyFolder(examCode, assemblyId);
        Path paperPath = folder.resolve("question-paper.json").normalize();
        try {
            Files.createDirectories(folder);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(paperPath.toFile(), paper);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store selected paper assembly.");
        }
        List<SelectedQuestionSummary> summaries = selectedSummariesFromExport(exportQuestions);
        QBankAssemblyRecord record = new QBankAssemblyRecord(
                examCode,
                assemblyId,
                request.qbankId(),
                paperPath.toString(),
                null,
                selected.size(),
                round(totalQuestionMarks(selected)),
                0,
                false,
                writeJson(request.topics() == null ? List.of() : request.topics(), "Could not store qbank blueprint in database."),
                writeJson(summaries, "Could not store selected question summary in database."),
                writeJson(paper, "Could not store selected paper snapshot in database."),
                ""
        );
        record.setPrivateLineageJson(writeJson(
                assemblyLineage(exportQuestions),
                "Could not store private assembly identity lineage."
        ));
        assemblyRepository.save(record);
        return new QBankAssemblyResponse(
                examCode,
                assemblyId,
                request.qbankId(),
                selected.size(),
                round(totalQuestionMarks(selected)),
                0,
                false,
                "/api/exams/" + encode(examCode) + "/qbank/assemblies/" + encode(assemblyId) + "/question-paper",
                "",
                summaries
        );
    }

    @Transactional
    public QBankPublishResponse publish(String examCode, String assemblyId) {
        Exam exam = exam(examCode);
        QBankAssemblyRecord record = assemblyRecord(examCode, assemblyId);
        Path paperPath = Path.of(record.getQuestionPaperPath()).toAbsolutePath().normalize();
        guardInside(paperPath, assemblyRoot);
        if (!Files.exists(paperPath)) {
            throw new IllegalArgumentException("Selected paper assembly not found.");
        }
        JsonNode paper = readJson(paperPath, assemblyRoot, "Could not load selected question paper.");
        String baseName = slug(exam.getName().isBlank() ? exam.getExamCode() : exam.getName());
        QuestionPaperStorageService.StoredQuestionPaper storedPaper = questionPaperStorageService.storeGenerated(
                exam.getExamCode(),
                paper,
                baseName + "_question_paper.json"
        );
        exam.attachQuestionPaper(storedPaper.path(), storedPaper.originalName(), storedPaper.questionCount());
        // A newly published paper must never inherit a key from an older assembly.
        exam.clearAnswerKey();
        record.markPublished();
        assemblyRepository.save(record);
        examRepository.save(exam);
        return new QBankPublishResponse(
                exam.getExamCode(),
                assemblyId,
                storedPaper.questionCount(),
                round(paper.path("total_marks").asDouble(0)),
                true,
                false,
                "Selected paper published. The source key remains sealed on the server; reconstruct the evaluation key from this exact paper after the exam."
        );
    }

    public DownloadedAssemblyFile downloadAssemblyFile(String examCode, String assemblyId, String kind) {
        Exam exam = exam(examCode);
        QBankAssemblyRecord record = assemblyRecord(examCode, assemblyId);
        String pathText = "answer-key".equals(kind) ? record.getAnswerKeyPath() : record.getQuestionPaperPath();
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException("Selected assembly file not found.");
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        guardInside(path, assemblyRoot);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Selected assembly file not found.");
        }
        try {
            String baseName = slug(exam.getName().isBlank() ? exam.getExamCode() : exam.getName());
            String downloadName = baseName + ("answer-key".equals(kind) ? "_answer_key.json" : "_question_paper.json");
            return new DownloadedAssemblyFile(downloadName, Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read selected assembly file.");
        }
    }

    public DownloadedAssemblyFile downloadAssemblyPdf(String examCode, String assemblyId, String kind) {
        Exam exam = exam(examCode);
        QBankAssemblyRecord record = assemblyRecord(examCode, assemblyId);
        String pathText = "answer-key".equals(kind) ? record.getAnswerKeyPath() : record.getQuestionPaperPath();
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException("Selected assembly file not found.");
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        guardInside(path, assemblyRoot);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Selected assembly file not found.");
        }
        JsonNode root = readJson(path, assemblyRoot, "Could not load selected assembly file.");
        String baseName = slug(exam.getName().isBlank() ? exam.getExamCode() : exam.getName());
        if ("answer-key".equals(kind)) {
            return new DownloadedAssemblyFile(baseName + "_answer_key.pdf", qbankPdfExportService.answerKeyPdf(exam, root));
        }
        return new DownloadedAssemblyFile(baseName + "_question_paper.pdf", qbankPdfExportService.questionPaperPdf(exam, root));
    }

    @Transactional
    public QBankAggregatedKeyResponse reconstructPublishedAnswerKey(String examCode) {
        Exam exam = exam(examCode);
        if (exam.getQuestionPaperJsonPath() == null || exam.getQuestionPaperJsonPath().isBlank()) {
            throw new IllegalStateException("Publish the exact served question paper before reconstructing its evaluation key.");
        }
        JsonNode fullKey = defaultAnswerKey(exam.getExamCode());
        if (fullKey == null) {
            throw new IllegalStateException("The server key vault has no source answer key for this exam.");
        }
        JsonNode paper = questionPaperStorageService.sanitizedPaper(exam);
        List<QBankQuestion> selected = normalizeQuestions(paper);
        if (selected.isEmpty()) {
            throw new IllegalStateException("Published question paper has no selectable question IDs.");
        }
        PaperAuthMetadata paperAuth = paperAuthFrom(paper);
        verifySourceKeyLineage(paper, fullKey, paperAuth);
        List<ExportQuestion> lineage = reconstructionLineage(exam.getExamCode(), paper, selected);
        ObjectNode key = buildSelectedKeyFromRoot(fullKey, selected, exam, List.of(), "", true, paperAuth, lineage);
        key.put("server_assembly_id", firstText(paper, "server_assembly_id", "serverAssemblyId"));
        validateReconstructedKey(paper, key);
        String assemblyId = "final-" + compactId();
        Path folder = assemblyFolder(exam.getExamCode(), assemblyId);
        Path paperPath = folder.resolve("question-paper.json").normalize();
        Path keyPath = folder.resolve("answer-key.json").normalize();
        try {
            Files.createDirectories(folder);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(paperPath.toFile(), paper);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(keyPath.toFile(), key);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store reconstructed final paper/key snapshot.");
        }
        String baseName = slug(exam.getName().isBlank() ? exam.getExamCode() : exam.getName());
        AnswerKeyStorageService.StoredAnswerKey storedKey = answerKeyStorageService.storeGenerated(
                exam.getExamCode(),
                key,
                baseName + "_answer_key.json"
        );
        exam.attachAnswerKey(storedKey.path(), storedKey.originalName(), storedKey.totalMarks());
        List<SelectedQuestionSummary> summaries = selectedSummaries(selected);
        QBankAssemblyRecord record = new QBankAssemblyRecord(
                exam.getExamCode(),
                assemblyId,
                "PUBLISHED_PAPER",
                paperPath.toString(),
                keyPath.toString(),
                selected.size(),
                round(totalQuestionMarks(selected)),
                key.path("answers").size(),
                true,
                "[]",
                writeJson(summaries, "Could not store final selected question summary in database."),
                writeJson(paper, "Could not store final selected paper snapshot in database."),
                writeJson(key, "Could not store final selected answer key snapshot in database.")
        );
        record.markPublished();
        assemblyRepository.save(record);
        examRepository.save(exam);
        return new QBankAggregatedKeyResponse(
                exam.getExamCode(),
                assemblyId,
                selected.size(),
                key.path("answers").size(),
                round(totalQuestionMarks(selected)),
                "/api/exams/" + encode(exam.getExamCode()) + "/qbank/assemblies/" + encode(assemblyId) + "/answer-key",
                "Evaluation key reconstructed from the exact published paper using verified source licence, global question IDs, and option IDs."
        );
    }

    /** Kept as a compatibility alias for older admin clients. */
    public QBankAggregatedKeyResponse aggregatePublishedAnswerKey(String examCode) {
        return reconstructPublishedAnswerKey(examCode);
    }

    public List<QBankAssemblyHistoryResponse> listAssemblies(String examCode) {
        exam(examCode);
        return assemblyRepository.findAllByExamCodeOrderByCreatedAtDesc(examCode)
                .stream()
                .map(record -> new QBankAssemblyHistoryResponse(
                        record.getExamCode(),
                        record.getAssemblyId(),
                        record.getQbankId(),
                        record.getQuestionCount(),
                        round(record.getTotalMarks()),
                        record.getMatchedAnswerCount(),
                        record.isAnswerKeyAvailable(),
                        record.getCreatedAt() == null ? "" : record.getCreatedAt().toString(),
                        record.getPublishedAt() == null ? "" : record.getPublishedAt().toString(),
                        readSelectedSummaries(record.getSelectedQuestionsJson())
                ))
                .toList();
    }

    private QBankAnalyzeResponse inventoryResponse(String examCode, String qbankId, String originalName, List<QBankQuestion> questions) {
        Map<TopicKey, TopicBucket> byTopic = new LinkedHashMap<>();
        LinkedHashSet<String> difficulties = new LinkedHashSet<>();
        for (QBankQuestion question : questions) {
            difficulties.add(question.difficulty());
            TopicKey key = new TopicKey(question.subject(), question.subjectCode(), question.section(), question.sectionCode(), question.topic(), question.topicCode());
            byTopic.computeIfAbsent(key, ignored -> new TopicBucket(key)).add(question);
        }
        List<TopicInventory> topics = byTopic.values().stream()
                .map(TopicBucket::toInventory)
                .sorted(Comparator
                        .comparing(TopicInventory::subject, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TopicInventory::section, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TopicInventory::topic, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new QBankAnalyzeResponse(
                examCode,
                qbankId,
                originalName,
                questions.size(),
                round(totalQuestionMarks(questions)),
                difficulties.stream().sorted(this::compareDifficulty).toList(),
                topics
        );
    }

    private List<QBankQuestion> normalizeQuestions(JsonNode root) {
        ArrayNode array = questionsArray(root);
        if (array == null) {
            return List.of();
        }
        List<QBankQuestion> out = new ArrayList<>();
        String rootSubject = text(root, "paper_subject", text(root, "subject", ""));
        String rootSubjectCode = text(root, "subject_short", "");
        String rootSection = text(root, "section", rootSubject);
        String rootSectionCode = text(root, "section_code", rootSubjectCode);
        for (JsonNode node : array) {
            String questionId = firstText(node, "question_id", "qid", "bank_question_id", "id");
            if (questionId.isBlank()) {
                questionId = "QBANK-" + (out.size() + 1);
            }
            String sourceQuestionId = firstText(node, "bank_question_id", "bankQuestionId", "source_question_id", "sourceQuestionId");
            if (sourceQuestionId.isBlank()) {
                sourceQuestionId = questionId;
            }
            String subject = firstText(node, "subject_name", "subjectName", "paper_subject", "subject", "bank_subject");
            if (subject.isBlank()) {
                subject = rootSubject.isBlank() ? "Subject" : rootSubject;
            }
            String subjectCode = firstText(node, "subject_short", "subject_code", "subjectCode", "subjectCodeShort");
            if (subjectCode.isBlank()) {
                subjectCode = rootSubjectCode;
            }
            String section = firstText(node, "section", "section_name", "sectionName");
            if (section.isBlank()) {
                section = rootSection.isBlank() ? subject : rootSection;
            }
            String sectionCode = firstText(node, "section_code", "sectionCode");
            if (sectionCode.isBlank()) {
                sectionCode = rootSectionCode;
            }
            String topic = firstText(node, "topic", "topic_name", "topicName", "chapter", "unit");
            String topicCode = firstText(node, "topic_code", "topicCode", "chapter_code", "unit_code");
            String difficulty = firstText(node, "difficulty", "difficulty_level", "difficultyLevel", "level");
            double marks = number(firstText(node, "marks", "positive_marks", "positiveMarks", "max_marks", "maxMarks"), 0);
            ObjectNode questionNode;
            if (node instanceof ObjectNode objectNode) {
                questionNode = objectNode.deepCopy();
            } else {
                questionNode = objectMapper.createObjectNode();
                questionNode.set("raw_question", node.deepCopy());
            }
            out.add(new QBankQuestion(
                    questionId,
                    sourceQuestionId,
                    subject,
                    subjectCode,
                    section,
                    sectionCode,
                    topic.isBlank() ? DEFAULT_TOPIC : topic,
                    topicCode,
                    normalizeDifficulty(difficulty),
                    marks,
                    existingOptionIds(questionNode),
                    questionNode
            ));
        }
        return out;
    }

    private List<QBankQuestion> selectQuestions(List<QBankQuestion> questions, QBankAssembleRequest request) {
        List<BlueprintTopicRequest> rows = request.topics() == null ? List.of() : request.topics();
        if (rows.isEmpty()) {
            return questions;
        }
        SecureRandom random = secureRandom(request.seed());
        List<QBankQuestion> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        for (BlueprintTopicRequest row : rows) {
            if (row == null || row.difficultyCounts() == null || row.difficultyCounts().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : row.difficultyCounts().entrySet()) {
                int requested = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
                if (requested == 0) {
                    continue;
                }
                String difficulty = normalizeDifficulty(entry.getKey());
                List<QBankQuestion> pool = questions.stream()
                        .filter(question -> !selectedIds.contains(question.questionId()))
                        .filter(question -> matchesBlueprint(row, question))
                        .filter(question -> question.difficulty().equalsIgnoreCase(difficulty))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (pool.size() < requested) {
                    throw new IllegalArgumentException("Not enough questions for " + label(row) + " / " + difficulty
                            + ". Requested " + requested + ", available " + pool.size() + ".");
                }
                pool.sort(Comparator.comparing(QBankQuestion::questionId));
                java.util.Collections.shuffle(pool, random);
                for (int index = 0; index < requested; index++) {
                    QBankQuestion picked = pool.get(index);
                    selected.add(picked);
                    selectedIds.add(picked.questionId());
                }
            }
        }
        selected.sort(Comparator
                .comparing(QBankQuestion::section, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingDouble(QBankQuestion::marks).reversed())
                .thenComparing(QBankQuestion::topic, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(QBankQuestion::difficulty, this::compareDifficulty)
                .thenComparing(QBankQuestion::questionId));
        return selected;
    }

    private ObjectNode buildSelectedPaper(JsonNode qbank, List<ExportQuestion> selected, Exam exam, QBankAssembleRequest request, PaperAuthMetadata auth, String assemblyId) {
        ObjectNode paper = objectMapper.createObjectNode();
        if (qbank instanceof ObjectNode source) {
            source.fields().forEachRemaining(entry -> {
                if (!"questions".equals(entry.getKey()) && !"answers".equals(entry.getKey()) && !"qs".equals(entry.getKey())) {
                    paper.set(entry.getKey(), entry.getValue().deepCopy());
                }
            });
        }
        removePrivateExportFields(paper);
        stampPaperAuth(paper, auth);
        paper.put("type", "QUESTION_PAPER");
        paper.put("exam", exam.getName());
        paper.put("exam_name", exam.getName());
        paper.put("project_name", exam.getName());
        paper.put("export_role", "question_paper");
        paper.put("generated_by", "server_qbank_assembler");
        paper.put("server_assembly_id", assemblyId);
        paper.put("generated_at", Instant.now().toString());
        paper.put("selection_seed", request.seed() == null ? "" : request.seed().trim());
        paper.put("total_questions", selected.size());
        paper.put("total_marks", round(selected.stream().mapToDouble(question -> question.source().marks()).sum()));
        paper.set("selection_blueprint", objectMapper.valueToTree(request.topics() == null ? List.of() : request.topics()));
        ArrayNode questionArray = objectMapper.createArrayNode();
        for (ExportQuestion question : selected) {
            ObjectNode copy = question.source().node().deepCopy();
            copy.put("question_number", question.questionNumber());
            copy.put("question_id", question.exportQuestionId());
            copy.put("bank_question_id", question.source().sourceQuestionId());
            copy.put("source_question_id", question.source().sourceQuestionId());
            copy.put("subject_name", question.source().subject());
            copy.put("subject_short", question.source().subjectCode());
            copy.put("section", question.source().section());
            copy.put("section_code", question.source().sectionCode());
            copy.put("topic", question.source().topic());
            copy.put("topic_code", question.source().topicCode());
            copy.put("difficulty", question.source().difficulty());
            copy.put("marks", question.source().marks());
            remapOptionContent(copy, question.optionOrder());
            copy.set("option_ids", objectMapper.valueToTree(question.optionIds()));
            removeAnswerFields(copy);
            questionArray.add(copy);
        }
        paper.set("questions", questionArray);
        return paper;
    }

    private ObjectNode buildSelectedKey(String examCode, JsonNode qbank, List<ExportQuestion> selected, Exam exam, QBankAssembleRequest request, PaperAuthMetadata auth) {
        JsonNode keyRoot = defaultAnswerKey(examCode);
        if (keyRoot == null && answersArray(qbank) != null && !answersArray(qbank).isEmpty()) {
            keyRoot = qbank;
        }
        return buildSelectedKeyFromRoot(
                keyRoot,
                selected.stream().map(ExportQuestion::source).toList(),
                exam,
                request.topics() == null ? List.of() : request.topics(),
                request.seed() == null ? "" : request.seed().trim(),
                false,
                auth,
                selected
        );
    }

    private ObjectNode buildSelectedKeyFromRoot(JsonNode keyRoot, List<QBankQuestion> selected, Exam exam, Object blueprint, String seed, boolean requireAllAnswers, PaperAuthMetadata auth) {
        return buildSelectedKeyFromRoot(keyRoot, selected, exam, blueprint, seed, requireAllAnswers, auth, List.of());
    }

    private ObjectNode buildSelectedKeyFromRoot(JsonNode keyRoot, List<QBankQuestion> selected, Exam exam, Object blueprint, String seed, boolean requireAllAnswers, PaperAuthMetadata auth, List<ExportQuestion> exportQuestions) {
        ArrayNode answers = answersArray(keyRoot);
        if (answers == null || answers.isEmpty()) {
            return null;
        }
        Map<String, List<JsonNode>> byQuestionId = new LinkedHashMap<>();
        for (JsonNode answer : answers) {
            for (String id : questionIdAliases(answer)) {
                byQuestionId.computeIfAbsent(id, ignored -> new ArrayList<>()).add(answer);
            }
        }
        ObjectNode key = objectMapper.createObjectNode();
        if (keyRoot instanceof ObjectNode source) {
            source.fields().forEachRemaining(entry -> {
                if (!"answers".equals(entry.getKey()) && !"questions".equals(entry.getKey()) && !"qs".equals(entry.getKey())) {
                    key.set(entry.getKey(), entry.getValue().deepCopy());
                }
            });
        }
        removePrivateExportFields(key);
        stampAnswerKeyAuth(key, auth);
        key.put("type", "ANSWER_KEY");
        key.put("exam", exam.getName());
        key.put("exam_name", exam.getName());
        key.put("project_name", exam.getName());
        key.put("export_role", "answer_key");
        key.put("generated_by", "server_qbank_assembler");
        key.put("generated_at", Instant.now().toString());
        key.put("selection_seed", seed == null ? "" : seed.trim());
        key.put("total_marks", round(totalQuestionMarks(selected)));
        key.set("selection_blueprint", objectMapper.valueToTree(blueprint == null ? List.of() : blueprint));
        ArrayNode selectedAnswers = objectMapper.createArrayNode();
        List<String> missing = new ArrayList<>();
        Map<String, List<ExportQuestion>> exportsBySourceId = new LinkedHashMap<>();
        for (ExportQuestion exportQuestion : exportQuestions) {
            exportsBySourceId.computeIfAbsent(
                    clean(exportQuestion.source().sourceQuestionId()),
                    ignored -> new ArrayList<>()
            ).add(exportQuestion);
        }
        Map<String, Integer> exportOccurrenceBySourceId = new LinkedHashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            QBankQuestion question = selected.get(index);
            JsonNode answer = answerForQuestion(byQuestionId, question);
            if (answer == null) {
                missing.add(question.sourceQuestionId());
                continue;
            }
            ObjectNode copy = answer.deepCopy();
            ExportQuestion exportQuestion = takeNextBySourceId(
                    exportsBySourceId,
                    exportOccurrenceBySourceId,
                    question.sourceQuestionId()
            );
            String exportQuestionId = exportQuestion == null ? question.questionId() : exportQuestion.exportQuestionId();
            String questionNumber = exportQuestion == null ? "Q." + (index + 1) : exportQuestion.questionNumber();
            Map<String, String> optionIds = exportQuestion == null ? question.optionIds() : exportQuestion.optionIds();
            copy.put("question_number", questionNumber);
            copy.put("question_id", exportQuestionId);
            copy.put("bank_question_id", question.sourceQuestionId());
            copy.put("source_question_id", question.sourceQuestionId());
            copy.put("section", question.section());
            copy.put("topic", question.topic());
            copy.put("topic_code", question.topicCode());
            copy.put("difficulty", question.difficulty());
            copy.put("marks", question.marks());
            copy.set("option_ids", objectMapper.valueToTree(optionIds));
            String questionType = firstText(question.node(), "question_type", "questionType", "type");
            if (!isNatType(questionType)) {
                List<String> sourceCorrectOptions = sourceCorrectOptions(answer);
                if (sourceCorrectOptions.isEmpty()) {
                    throw incompleteSourceAnswer(answer, question);
                }
                List<String> displayedCorrectOptions = exportQuestion == null
                        ? sourceCorrectOptions
                        : displayCorrectOptions(sourceCorrectOptions, exportQuestion.optionOrder());
                copy.set("correct_options", objectMapper.valueToTree(displayedCorrectOptions));
                copy.set("correct_option_ids", objectMapper.valueToTree(correctOptionIds(copy, optionIds)));
            } else if (!hasUsableNatAnswer(answer)) {
                throw incompleteSourceAnswer(answer, question);
            }
            selectedAnswers.add(copy);
        }
        if (requireAllAnswers && !missing.isEmpty()) {
            throw new IllegalStateException("Full-bank answer key is missing " + missing.size()
                    + " selected question ID(s): " + String.join(", ", missing.stream().limit(12).toList())
                    + (missing.size() > 12 ? "..." : ""));
        }
        if (selectedAnswers.isEmpty()) {
            return null;
        }
        key.set("answers", selectedAnswers);
        return key;
    }

    private List<AssemblyLineageEntry> assemblyLineage(List<ExportQuestion> questions) {
        return questions.stream().map(question -> new AssemblyLineageEntry(
                question.source().sourceQuestionId(),
                question.source().questionId(),
                question.exportQuestionId(),
                question.questionNumber(),
                question.optionIds(),
                question.optionOrder()
        )).toList();
    }

    private List<ExportQuestion> reconstructionLineage(String examCode, JsonNode paper, List<QBankQuestion> selected) {
        String assemblyId = firstText(paper, "server_assembly_id", "serverAssemblyId");
        if (assemblyId.isBlank()) {
            throw new IllegalStateException("Published paper has no server assembly identity. Republish it from the qbank assembly ledger.");
        }
        QBankAssemblyRecord record = assemblyRepository.findByExamCodeAndAssemblyId(examCode, assemblyId)
                .orElseThrow(() -> new IllegalStateException("Server assembly lineage was not found for the published paper."));
        if (record.getPrivateLineageJson() == null || record.getPrivateLineageJson().isBlank()) {
            throw new IllegalStateException("This assembly predates protected option lineage. Reassemble and republish the paper before evaluation.");
        }
        List<AssemblyLineageEntry> entries;
        try {
            entries = objectMapper.readValue(record.getPrivateLineageJson(), new TypeReference<List<AssemblyLineageEntry>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("Protected assembly lineage is unreadable.");
        }
        Map<String, QBankQuestion> selectedByExportId = selected.stream().collect(Collectors.toMap(
                QBankQuestion::questionId,
                question -> question,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        List<ExportQuestion> out = new ArrayList<>();
        for (AssemblyLineageEntry entry : entries) {
            QBankQuestion question = selectedByExportId.get(clean(entry.exportQuestionId()));
            if (question == null || !clean(entry.sourceQuestionId()).equals(clean(question.sourceQuestionId()))) {
                throw new IllegalStateException("Published paper and protected assembly lineage have different question IDs.");
            }
            String sourceQuestionKey = clean(entry.sourceQuestionKey());
            if (!sourceQuestionKey.isBlank()) {
                question.node().put("source_question_key", sourceQuestionKey);
            }
            out.add(new ExportQuestion(
                    question,
                    clean(entry.exportQuestionId()),
                    clean(entry.questionNumber()),
                    entry.optionIds() == null ? Map.of() : new LinkedHashMap<>(entry.optionIds()),
                    entry.optionOrder() == null ? List.of() : List.copyOf(entry.optionOrder())
            ));
        }
        if (out.size() != selected.size()) {
            throw new IllegalStateException("Published paper and protected assembly lineage have different question counts.");
        }
        return out;
    }

    private JsonNode defaultAnswerKey(String examCode) {
        Path path = qbankFolder(examCode).resolve("default-answer-key.json").normalize();
        guardInside(path, qbankRoot);
        if (!Files.exists(path)) {
            return null;
        }
        return readJson(path, qbankRoot, "Could not load full-bank answer key.");
    }

    private static boolean matchesBlueprint(BlueprintTopicRequest row, QBankQuestion question) {
        String requestedTopicCode = clean(row.topicCode());
        String requestedTopic = clean(row.topic());
        String requestedSection = clean(row.section());
        String requestedSubject = clean(row.subject());
        if (!requestedTopicCode.isBlank() && !requestedTopicCode.equalsIgnoreCase(question.topicCode())) {
            return false;
        }
        if (!requestedTopic.isBlank() && !requestedTopic.equalsIgnoreCase(question.topic())) {
            return false;
        }
        if (!requestedSection.isBlank() && !requestedSection.equalsIgnoreCase(question.section())) {
            return false;
        }
        return requestedSubject.isBlank() || requestedSubject.equalsIgnoreCase(question.subject());
    }

    private static String label(BlueprintTopicRequest row) {
        String topic = clean(row.topic());
        String section = clean(row.section());
        if (!topic.isBlank() && !section.isBlank()) {
            return section + " / " + topic;
        }
        if (!topic.isBlank()) {
            return topic;
        }
        return section.isBlank() ? "selected topic" : section;
    }

    private List<SelectedQuestionSummary> selectedSummaries(List<QBankQuestion> selected) {
        List<SelectedQuestionSummary> out = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            QBankQuestion question = selected.get(index);
            out.add(new SelectedQuestionSummary(
                    question.questionId(),
                    "Q." + (index + 1),
                    question.subject(),
                    question.section(),
                    question.topic(),
                    question.topicCode(),
                    question.difficulty(),
                    question.marks()
            ));
        }
        return out;
    }

    private List<SelectedQuestionSummary> selectedSummariesFromExport(List<ExportQuestion> selected) {
        List<SelectedQuestionSummary> out = new ArrayList<>();
        for (ExportQuestion question : selected) {
            out.add(new SelectedQuestionSummary(
                    question.exportQuestionId(),
                    question.questionNumber(),
                    question.source().subject(),
                    question.source().section(),
                    question.source().topic(),
                    question.source().topicCode(),
                    question.source().difficulty(),
                    question.source().marks()
            ));
        }
        return out;
    }

    private Exam exam(String examCode) {
        String normalized = clean(examCode);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Exam code is required.");
        }
        return examRepository.findByExamCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + normalized));
    }

    private QBankAssemblyRecord assemblyRecord(String examCode, String assemblyId) {
        String normalizedExamCode = clean(examCode);
        String normalizedAssemblyId = clean(assemblyId);
        if (normalizedAssemblyId.isBlank()) {
            throw new IllegalArgumentException("Selected paper assembly id is required.");
        }
        return assemblyRepository.findByExamCodeAndAssemblyId(normalizedExamCode, normalizedAssemblyId)
                .orElseThrow(() -> new IllegalArgumentException("Selected paper assembly is not registered in the database."));
    }

    private Path qbankFolder(String examCode) {
        Path folder = qbankRoot.resolve(safeSegment(examCode)).normalize();
        guardInside(folder, qbankRoot);
        return folder;
    }

    private Path qbankPath(String examCode, String qbankId) {
        Path path = qbankFolder(examCode).resolve(safeSegment(qbankId) + ".json").normalize();
        guardInside(path, qbankRoot);
        return path;
    }

    private Path assemblyFolder(String examCode, String assemblyId) {
        Path folder = assemblyRoot.resolve(safeSegment(examCode)).resolve(safeSegment(assemblyId)).normalize();
        guardInside(folder, assemblyRoot);
        return folder;
    }

    private JsonNode readJson(Path path, Path allowedRoot, String errorMessage) {
        Path normalized = path.toAbsolutePath().normalize();
        guardInside(normalized, allowedRoot);
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return objectMapper.readTree(normalized.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private static void guardInside(Path path, Path root) {
        Path normalized = path.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Invalid storage path.");
        }
    }

    private static ArrayNode questionsArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode questions = root.get("questions");
        if (questions instanceof ArrayNode array) {
            return array;
        }
        JsonNode qs = root.get("qs");
        if (qs instanceof ArrayNode array) {
            return array;
        }
        return null;
    }

    private static ArrayNode answersArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode answers = root.get("answers");
        if (answers instanceof ArrayNode array) {
            return array;
        }
        JsonNode qs = root.get("qs");
        if (qs instanceof ArrayNode studioQuestions) {
            return studioAnswersArray(studioQuestions);
        }
        return null;
    }

    private static ArrayNode studioAnswersArray(ArrayNode studioQuestions) {
        ArrayNode answers = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < studioQuestions.size(); index++) {
            JsonNode node = studioQuestions.get(index);
            if (!(node instanceof ObjectNode question)) {
                continue;
            }
            ObjectNode answer = JsonNodeFactory.instance.objectNode();
            String sourceQuestionId = firstText(question, "qid", "question_id", "questionId", "id");
            if (sourceQuestionId.isBlank()) {
                sourceQuestionId = "QBANK-" + (index + 1);
            }
            String questionType = firstText(question, "question_type", "questionType", "type");
            String section = firstText(question, "section", "section_name", "sectionName", "subject_name", "subjectName", "subject");
            String topic = firstText(question, "topic", "topic_name", "topicName");
            String topicCode = firstText(question, "topicCode", "topic_code");
            String difficulty = firstText(question, "difficulty", "difficulty_level", "difficultyLevel");
            double marks = number(firstText(question, "marks", "positive_marks", "positiveMarks"), 0);
            double negativeMarks = number(firstText(question, "negative_marks", "negativeMarks", "negMarks"), 0);
            Map<String, String> optionIds = existingOptionIds(question);
            List<String> letters = detectedOptionLetters(question);
            if (optionIds.isEmpty() && !letters.isEmpty()) {
                for (int optionIndex = 0; optionIndex < letters.size(); optionIndex++) {
                    String displayLetter = String.valueOf((char) ('A' + optionIndex));
                    optionIds.put(displayLetter, sourceQuestionId + "_OP" + displayLetter + (optionIndex + 1));
                }
            }
            answer.put("section", section);
            answer.put("question_number", "Q." + (index + 1));
            answer.put("question_id", sourceQuestionId);
            answer.put("bank_question_id", sourceQuestionId);
            answer.put("source_question_id", sourceQuestionId);
            answer.put("topic", topic);
            answer.put("topic_code", topicCode);
            answer.put("difficulty", normalizeDifficulty(difficulty));
            answer.put("question_type", questionType);
            answer.put("marks", marks);
            answer.put("negative_marks", negativeMarks);
            ObjectNode optionIdsNode = JsonNodeFactory.instance.objectNode();
            optionIds.forEach(optionIdsNode::put);
            answer.set("option_ids", optionIdsNode);
            if (isNatType(questionType)) {
                answer.put("correct_answer", firstText(question, "natAnswer", "nat_answer", "correct_answer", "correctAnswer", "answer"));
            } else {
                ArrayNode correctOptions = JsonNodeFactory.instance.arrayNode();
                ArrayNode correctOptionIds = JsonNodeFactory.instance.arrayNode();
                Set<String> expectedIds = new LinkedHashSet<>();
                JsonNode explicitCorrectIds = question.get("correctOptionIds");
                if (explicitCorrectIds != null && explicitCorrectIds.isArray()) {
                    explicitCorrectIds.forEach(value -> {
                        String optionId = clean(value.asText(""));
                        if (!optionId.isBlank()) {
                            expectedIds.add(optionId);
                        }
                    });
                }
                JsonNode options = question.get("options");
                if (options != null && options.isArray()) {
                    for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                        JsonNode option = options.get(optionIndex);
                        String displayLetter = String.valueOf((char) ('A' + optionIndex));
                        String optionId = firstNonBlank(
                                optionIds.get(displayLetter),
                                firstText(option, "oid", "option_id", "optionId", "id")
                        );
                        boolean correct = option.path("correct").asBoolean(false);
                        if (!correct && !optionId.isBlank()) {
                            correct = expectedIds.contains(optionId);
                        }
                        if (correct) {
                            correctOptions.add(displayLetter);
                            if (!optionId.isBlank()) {
                                correctOptionIds.add(optionId);
                            }
                        }
                    }
                }
                if (!correctOptions.isEmpty()) {
                    answer.set("correct_options", correctOptions);
                }
                if (!correctOptionIds.isEmpty()) {
                    answer.set("correct_option_ids", correctOptionIds);
                }
            }
            answers.add(answer);
        }
        return answers;
    }

    private static boolean isNatType(String questionType) {
        String type = clean(questionType).toUpperCase(Locale.ROOT);
        return type.contains("NAT") || type.contains("NUM");
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static List<String> questionIdAliases(JsonNode node) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String field : List.of(
                "question_id", "questionId", "qid", "bank_question_id", "bankQuestionId",
                "source_question_id", "sourceQuestionId", "legacy_question_id", "legacyQuestionId",
                "source_question_key", "sourceQuestionKey",
                "export_question_alias", "exportQuestionAlias", "id")) {
            String value = firstText(node, field);
            if (!value.isBlank()) {
                ids.add(value);
            }
        }
        return new ArrayList<>(ids);
    }

    private static <T> T takeNextBySourceId(Map<String, List<T>> valuesBySourceId,
                                             Map<String, Integer> occurrenceBySourceId,
                                             String sourceId) {
        String normalized = clean(sourceId);
        List<T> values = valuesBySourceId.getOrDefault(normalized, List.of());
        int occurrence = occurrenceBySourceId.getOrDefault(normalized, 0);
        occurrenceBySourceId.put(normalized, occurrence + 1);
        return occurrence < values.size() ? values.get(occurrence) : null;
    }

    private static JsonNode answerForQuestion(Map<String, List<JsonNode>> answersById, QBankQuestion question) {
        LinkedHashSet<JsonNode> candidates = new LinkedHashSet<>();
        LinkedHashSet<String> aliases = new LinkedHashSet<>(questionIdAliases(question.node()));
        aliases.add(question.questionId());
        aliases.add(question.sourceQuestionId());
        for (String alias : aliases) {
            candidates.addAll(answersById.getOrDefault(clean(alias), List.of()));
        }
        if (candidates.size() <= 1) {
            return candidates.stream().findFirst().orElse(null);
        }

        String servedType = firstText(question.node(), "question_type", "questionType", "type");
        List<JsonNode> sameType = candidates.stream()
                .filter(answer -> sameQuestionType(servedType,
                        firstText(answer, "question_type", "questionType", "type")))
                .toList();
        if (sameType.size() == 1) {
            return sameType.getFirst();
        }
        throw new IllegalStateException("Source answer key has ambiguous duplicate IDs for served question "
                + question.questionId() + " (source " + question.sourceQuestionId() + "). Regenerate the master key with unique question IDs.");
    }

    private static boolean sameQuestionType(String left, String right) {
        String normalizedLeft = clean(left).toUpperCase(Locale.ROOT);
        String normalizedRight = clean(right).toUpperCase(Locale.ROOT);
        if (isNatType(normalizedLeft) || isNatType(normalizedRight)) {
            return isNatType(normalizedLeft) == isNatType(normalizedRight);
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private static boolean hasUsableNatAnswer(JsonNode answer) {
        String value = firstText(answer,
                "correct_answer", "correctAnswer", "nat_answer", "natAnswer", "answer");
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !normalized.equals("(not set)")
                && !normalized.equals("not set")
                && !normalized.equals("unset");
    }

    private static IllegalStateException incompleteSourceAnswer(JsonNode answer, QBankQuestion question) {
        String number = firstText(answer, "question_number", "questionNumber");
        String sourceId = question.sourceQuestionId();
        String prefix = number.isBlank() ? "Source answer key entry" : "Source answer key entry " + number;
        return new IllegalStateException(prefix + " (source " + sourceId + ") has no correct answer. "
                + "Set the answer in QS Studio, export a corrected master key, upload it here, and reconstruct again.");
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    private static double number(String value, double fallback) {
        String cleaned = clean(value)
                .replace("Marks:", "")
                .replace("marks:", "")
                .replace("Mark:", "")
                .replace("mark:", "")
                .replace("+", "")
                .replace("M", "")
                .replace("m", "")
                .trim();
        if (cleaned.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalizeDifficulty(String raw) {
        String text = clean(raw);
        if (text.isBlank()) {
            return DEFAULT_DIFFICULTY;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "e", "easy" -> "Easy";
            case "m", "med", "medium", "moderate" -> "Medium";
            case "h", "hard", "t", "tough", "difficult" -> "Tough";
            default -> Character.toUpperCase(text.charAt(0)) + text.substring(1);
        };
    }

    private int compareDifficulty(String left, String right) {
        return Integer.compare(difficultyRank(left), difficultyRank(right));
    }

    private static int difficultyRank(String difficulty) {
        String text = normalizeDifficulty(difficulty);
        if ("Easy".equals(text)) return 1;
        if ("Medium".equals(text)) return 2;
        if ("Tough".equals(text)) return 3;
        if (DEFAULT_DIFFICULTY.equals(text)) return 9;
        return 5;
    }

    private static double totalQuestionMarks(List<QBankQuestion> questions) {
        double total = 0;
        for (QBankQuestion question : questions) {
            total += question.marks();
        }
        return total;
    }

    private static double totalAnswerMarks(ArrayNode answers) {
        double total = 0;
        for (JsonNode answer : answers) {
            total += answer.path("marks").asDouble(0);
        }
        return total;
    }

    private static void removeAnswerFields(ObjectNode node) {
        String[] fields = {
                "correct", "correct_answer", "correctAnswer", "answer", "answers",
                "answer_key", "answerKey", "correct_options", "correctOptions",
                "correct_option_ids", "correctOptionIds", "solution", "solutions", "explanation"
        };
        for (String field : fields) {
            node.remove(field);
        }
    }

    private static void removePrivateExportFields(ObjectNode node) {
        String[] fields = {
                "bank_id", "bankId", "export_set_id", "exportSetId",
                "paper_id", "paperId", "key_id", "keyId",
                "expected_pair", "expectedPair", "file_stem", "fileStem"
        };
        for (String field : fields) {
            node.remove(field);
        }
    }

    private List<ExportQuestion> buildExportQuestions(List<QBankQuestion> selected, QBankAssembleRequest request) {
        List<ExportQuestion> out = new ArrayList<>();
        String seed = request == null ? "" : clean(request.seed());
        boolean randomizeOptions = request != null && request.randomizeOptions();
        for (int index = 0; index < selected.size(); index++) {
            QBankQuestion question = selected.get(index);
            String exportQuestionId = questionIdPrefix(question) + randomDigits(10) + "QS" + (index + 1);
            List<String> optionOrder = orderedSourceLetters(question, seed + "|" + exportQuestionId, randomizeOptions);
            out.add(new ExportQuestion(
                    question,
                    exportQuestionId,
                    "Q." + (index + 1),
                    generatedOptionIds(optionOrder, exportQuestionId),
                    optionOrder
            ));
        }
        return out;
    }

    private static Map<String, String> existingOptionIds(JsonNode node) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode optionIds = node == null ? null : node.get("option_ids");
        if (optionIds != null && optionIds.isObject()) {
            optionIds.fields().forEachRemaining(entry -> {
                String letter = clean(entry.getKey()).toUpperCase(Locale.ROOT);
                String value = clean(entry.getValue().asText(""));
                if (letter.matches("^[A-Z]$") && !value.isBlank()) {
                    out.put(letter, value);
                }
            });
        }
        JsonNode options = node == null ? null : node.get("options");
        if (options != null && options.isArray()) {
            for (int index = 0; index < options.size(); index++) {
                String letter = String.valueOf((char) ('A' + index));
                String optionId = firstText(options.get(index), "oid", "option_id", "optionId", "id");
                if (!optionId.isBlank()) {
                    out.putIfAbsent(letter, optionId);
                }
            }
        } else if (options != null && options.isObject()) {
            options.fields().forEachRemaining(entry -> {
                String letter = clean(entry.getKey()).toUpperCase(Locale.ROOT);
                if (!letter.matches("^[A-Z]$")) {
                    return;
                }
                String optionId = firstText(entry.getValue(), "oid", "option_id", "optionId", "id");
                if (!optionId.isBlank()) {
                    out.putIfAbsent(letter, optionId);
                }
            });
        }
        return out;
    }

    private static Map<String, String> generatedOptionIds(List<String> optionOrder, String exportQuestionId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int index = 0; index < optionOrder.size(); index++) {
            String displayLetter = String.valueOf((char) ('A' + index));
            out.put(displayLetter, exportQuestionId + "_OP" + displayLetter + (index + 1));
        }
        return out;
    }

    private static List<String> orderedSourceLetters(QBankQuestion question, String seed, boolean randomizeOptions) {
        List<String> letters = new ArrayList<>(detectedOptionLetters(question.node()));
        if (!randomizeOptions || letters.size() <= 1) {
            return letters;
        }
        SecureRandom random = secureRandom(seed);
        for (int index = letters.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            String current = letters.get(index);
            letters.set(index, letters.get(swap));
            letters.set(swap, current);
        }
        return letters;
    }

    private static void remapOptionContent(ObjectNode copy, List<String> optionOrder) {
        if (copy == null || optionOrder == null || optionOrder.isEmpty()) {
            return;
        }
        JsonNode sourceOptionsNode = copy.get("options");
        Map<String, JsonNode> sourceByLetter = new LinkedHashMap<>();
        Map<String, String> textByLetter = new LinkedHashMap<>();
        if (sourceOptionsNode != null) {
            if (sourceOptionsNode.isArray()) {
                for (int index = 0; index < sourceOptionsNode.size(); index++) {
                    sourceByLetter.put(String.valueOf((char) ('A' + index)), sourceOptionsNode.get(index).deepCopy());
                }
            } else if (sourceOptionsNode.isObject()) {
                sourceOptionsNode.fields().forEachRemaining(entry -> sourceByLetter.put(clean(entry.getKey()).toUpperCase(Locale.ROOT), entry.getValue().deepCopy()));
            }
        }
        List<String> existingLetters = detectedOptionLetters(copy);
        for (String letter : existingLetters) {
            String lower = letter.toLowerCase(Locale.ROOT);
            textByLetter.put(letter, firstNonBlank(
                    text(copy, "option_" + lower, ""),
                    text(copy, "option" + letter, ""),
                    text(copy, lower, ""),
                    text(copy, letter, "")
            ));
        }
        for (String letter : existingLetters) {
            String lower = letter.toLowerCase(Locale.ROOT);
            copy.remove(List.of("option_" + lower, "option" + letter, lower, letter));
        }
        ArrayNode reorderedArray = JsonNodeFactory.instance.arrayNode();
        ObjectNode reorderedObject = JsonNodeFactory.instance.objectNode();
        for (int index = 0; index < optionOrder.size(); index++) {
            String sourceLetter = optionOrder.get(index);
            String displayLetter = String.valueOf((char) ('A' + index));
            JsonNode optionValue = sourceByLetter.get(sourceLetter);
            if (optionValue == null) {
                optionValue = JsonNodeFactory.instance.textNode(textByLetter.getOrDefault(sourceLetter, ""));
            }
            reorderedArray.add(optionValue == null ? JsonNodeFactory.instance.nullNode() : optionValue.deepCopy());
            reorderedObject.set(displayLetter, optionValue == null ? JsonNodeFactory.instance.nullNode() : optionValue.deepCopy());
            if (optionValue != null && optionValue.isTextual()) {
                copy.put("option_" + displayLetter.toLowerCase(Locale.ROOT), optionValue.asText(""));
            }
        }
        if (sourceOptionsNode != null) {
            copy.set("options", sourceOptionsNode.isArray() ? reorderedArray : reorderedObject);
        }
    }

    private static List<String> detectedOptionLetters(JsonNode node) {
        LinkedHashSet<String> letters = new LinkedHashSet<>();
        letters.addAll(existingOptionIds(node).keySet());
        JsonNode options = node == null ? null : node.get("options");
        if (options != null) {
            if (options.isArray()) {
                for (int index = 0; index < options.size(); index++) {
                    letters.add(String.valueOf((char) ('A' + index)));
                }
            } else if (options.isObject()) {
                options.fieldNames().forEachRemaining(key -> {
                    String letter = clean(key).toUpperCase(Locale.ROOT);
                    if (letter.matches("^[A-Z]$")) {
                        letters.add(letter);
                    }
                });
            }
        }
        for (String letter : List.of("A", "B", "C", "D", "E", "F")) {
            if (!text(node, "option_" + letter.toLowerCase(Locale.ROOT), "").isBlank()
                    || !text(node, "option" + letter, "").isBlank()
                    || !text(node, letter.toLowerCase(Locale.ROOT), "").isBlank()
                    || !text(node, letter, "").isBlank()) {
                letters.add(letter);
            }
        }
        return new ArrayList<>(letters);
    }

    private static List<String> correctOptionIds(JsonNode answerNode, Map<String, String> optionIds) {
        List<String> out = new ArrayList<>();
        JsonNode correctOptions = firstArray(answerNode, "correct_options", "correctOptions");
        if (correctOptions != null && correctOptions.isArray()) {
            correctOptions.forEach(value -> {
                String letter = clean(value.asText("")).toUpperCase(Locale.ROOT);
                String optionId = optionIds.get(letter);
                if (optionId != null && !optionId.isBlank()) {
                    out.add(optionId);
                }
            });
        }
        return out;
    }

    private static List<String> displayCorrectOptions(JsonNode answerNode, List<String> optionOrder) {
        return displayCorrectOptions(sourceCorrectOptions(answerNode), optionOrder);
    }

    private static List<String> displayCorrectOptions(List<String> sourceCorrectOptions, List<String> optionOrder) {
        List<String> out = new ArrayList<>();
        if (sourceCorrectOptions == null || sourceCorrectOptions.isEmpty() || optionOrder == null || optionOrder.isEmpty()) {
            return out;
        }
        Set<String> correctSourceLetters = sourceCorrectOptions.stream()
                .map(value -> clean(value).toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (int index = 0; index < optionOrder.size(); index++) {
            if (correctSourceLetters.contains(clean(optionOrder.get(index)).toUpperCase(Locale.ROOT))) {
                out.add(String.valueOf((char) ('A' + index)));
            }
        }
        return out;
    }

    private static List<String> sourceCorrectOptions(JsonNode answerNode) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JsonNode correctOptions = firstArray(answerNode, "correct_options", "correctOptions");
        if (correctOptions != null) {
            correctOptions.forEach(value -> {
                String letter = clean(value.asText("")).toUpperCase(Locale.ROOT);
                if (letter.matches("^[A-Z]$")) {
                    out.add(letter);
                }
            });
        }
        if (!out.isEmpty()) {
            return new ArrayList<>(out);
        }

        LinkedHashSet<String> correctIds = new LinkedHashSet<>();
        for (String field : List.of(
                "source_correct_option_ids", "sourceCorrectOptionIds",
                "correct_option_ids", "correctOptionIds")) {
            JsonNode values = answerNode == null ? null : answerNode.get(field);
            if (values != null && values.isArray()) {
                values.forEach(value -> {
                    String id = clean(value.asText(""));
                    if (!id.isBlank()) {
                        correctIds.add(id);
                    }
                });
            }
        }
        if (correctIds.isEmpty()) {
            return List.of();
        }
        for (String field : List.of(
                "source_option_ids", "sourceOptionIds", "legacy_option_ids", "legacyOptionIds",
                "option_ids", "optionIds", "bank_option_ids", "bankOptionIds")) {
            JsonNode ids = answerNode == null ? null : answerNode.get(field);
            if (ids == null || !ids.isObject()) {
                continue;
            }
            ids.fields().forEachRemaining(entry -> {
                String letter = clean(entry.getKey()).toUpperCase(Locale.ROOT);
                String id = clean(entry.getValue().asText(""));
                if (letter.matches("^[A-Z]$") && correctIds.contains(id)) {
                    out.add(letter);
                }
            });
        }
        return new ArrayList<>(out);
    }

    private static JsonNode firstArray(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private static String questionIdPrefix(QBankQuestion question) {
        String raw = clean(question.subjectCode()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (raw.isBlank()) {
            raw = clean(question.sectionCode()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        }
        if (raw.isBlank()) {
            raw = clean(question.section()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        }
        if (raw.isBlank()) {
            raw = "GEN";
        }
        return raw.length() > 6 ? raw.substring(0, 6) : raw;
    }

    private static String randomDigits(int count) {
        StringBuilder out = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            out.append(SECURE_RANDOM.nextInt(10));
        }
        return out.toString();
    }

    private static PaperAuthMetadata newPaperAuth(String sourcePaperLicenseKey) {
        Instant now = Instant.now();
        long micros = now.getEpochSecond() * 1_000_000L + (now.getNano() / 1_000L);
        String licenseKey = randomHex(4) + "-" + randomHex(4) + "-" + randomHex(4) + "-" + randomHex(4) + "-" + randomHex(4);
        return new PaperAuthMetadata(
                licenseKey,
                micros,
                now.toString(),
                clean(sourcePaperLicenseKey),
                "skr_" + randomAlphaNumeric(40)
        );
    }

    private static PaperAuthMetadata paperAuthFrom(JsonNode paper) {
        String licenseKey = firstText(paper, "license_key", "paper_license_key", "licenseKey", "paperLicenseKey");
        if (licenseKey.isBlank()) {
            return null;
        }
        String generatedAt = firstText(paper, "generated_at", "generatedAt");
        String microsText = firstText(paper, "license_created_epoch_micros", "paper_created_epoch_micros", "created_epoch_micros");
        long micros = 0;
        try {
            micros = microsText.isBlank() ? 0L : Long.parseLong(microsText);
        } catch (NumberFormatException ignored) {
            micros = 0L;
        }
        String sourcePaperLicenseKey = firstText(paper,
                "source_paper_license_key", "sourcePaperLicenseKey", "source_license_key", "sourceLicenseKey");
        String serverKeyReference = firstText(paper, "server_key_reference", "serverKeyReference");
        return new PaperAuthMetadata(licenseKey, micros, generatedAt, sourcePaperLicenseKey, serverKeyReference);
    }

    private static void stampPaperAuth(ObjectNode node, PaperAuthMetadata auth) {
        if (auth == null) {
            return;
        }
        node.put("license_key", auth.licenseKey());
        node.put("paper_license_key", auth.licenseKey());
        node.put("license_created_epoch_micros", auth.createdAtMicros());
        node.put("paper_created_epoch_micros", auth.createdAtMicros());
        node.put("license_auth_required", true);
        node.put("source_paper_license_key", auth.sourcePaperLicenseKey());
        node.put("server_key_reference", auth.serverKeyReference());
        node.put("evaluation_key_status", "SERVER_HELD_PENDING_RECONSTRUCTION");
        node.put("answer_key_embedded", false);
        node.remove(List.of("answer_key_instance_key", "answer_key_license_key", "answers", "answer_key"));
    }

    private static void stampAnswerKeyAuth(ObjectNode node, PaperAuthMetadata auth) {
        if (auth == null) {
            return;
        }
        node.put("license_key", auth.licenseKey());
        node.put("paper_license_key", auth.licenseKey());
        node.put("license_created_epoch_micros", auth.createdAtMicros());
        node.put("paper_created_epoch_micros", auth.createdAtMicros());
        node.put("answer_key_created_epoch_micros", epochMicros(Instant.now()));
        node.put("license_auth_required", true);
        node.put("paired_paper_license_key", auth.licenseKey());
        node.put("source_license_key", auth.sourcePaperLicenseKey());
        node.put("source_paper_license_key", auth.sourcePaperLicenseKey());
        node.put("server_key_reference", auth.serverKeyReference());
        node.put("evaluation_key_status", "RECONSTRUCTED");
    }

    private static String sourcePaperLicense(JsonNode root) {
        return firstText(root,
                "paired_paper_license_key", "pairedPaperLicenseKey",
                "paper_license_key", "paperLicenseKey",
                "license_key", "licenseKey");
    }

    private static String sourceKeyPaperLicense(JsonNode root) {
        return firstText(root,
                "paired_paper_license_key", "pairedPaperLicenseKey",
                "source_paper_license_key", "sourcePaperLicenseKey",
                "source_license_key", "sourceLicenseKey",
                "paper_license_key", "paperLicenseKey",
                "license_key", "licenseKey");
    }

    private static void verifySourceKeyLineage(JsonNode paper, JsonNode sourceKey, PaperAuthMetadata auth) {
        if (auth == null || auth.licenseKey().isBlank() || auth.serverKeyReference().isBlank()) {
            throw new IllegalStateException("Published paper has no protected server key reference.");
        }
        String paperSource = clean(auth.sourcePaperLicenseKey());
        String keySource = sourceKeyPaperLicense(sourceKey);
        if (!paperSource.isBlank() && (keySource.isBlank() || !paperSource.equals(keySource))) {
            throw new IllegalStateException("Source answer key licence does not match the qbank used for this served paper.");
        }
        String assemblyId = firstText(paper, "server_assembly_id", "serverAssemblyId");
        if (assemblyId.isBlank()) {
            throw new IllegalStateException("Published paper has no protected assembly ID.");
        }
    }

    private static void validateReconstructedKey(JsonNode paper, JsonNode key) {
        ArrayNode paperQuestions = questionsArray(paper);
        ArrayNode keyAnswers = answersArray(key);
        if (paperQuestions == null || keyAnswers == null || paperQuestions.size() != keyAnswers.size()) {
            throw new IllegalStateException("Reconstructed key does not contain exactly one answer for every served question.");
        }
        Map<String, JsonNode> paperById = new LinkedHashMap<>();
        for (JsonNode question : paperQuestions) {
            String id = firstText(question, "question_id", "questionId", "qid");
            if (id.isBlank() || paperById.putIfAbsent(id, question) != null) {
                throw new IllegalStateException("Served paper contains a blank or duplicate global question ID.");
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode answer : keyAnswers) {
            String id = firstText(answer, "question_id", "questionId", "qid");
            JsonNode question = paperById.get(id);
            if (question == null || !seen.add(id)) {
                throw new IllegalStateException("Reconstructed key question IDs do not exactly match the served paper.");
            }
            String type = firstText(question, "question_type", "questionType", "type");
            if (!isNatType(type)) {
                Set<String> allowedIds = new LinkedHashSet<>();
                JsonNode optionIds = question.get("option_ids");
                if (optionIds != null && optionIds.isObject()) {
                    optionIds.elements().forEachRemaining(value -> allowedIds.add(clean(value.asText(""))));
                }
                JsonNode correctIds = answer.get("correct_option_ids");
                if (correctIds == null || !correctIds.isArray() || correctIds.isEmpty()) {
                    throw new IllegalStateException("Reconstructed key is missing correct option IDs for question " + id + ".");
                }
                for (JsonNode value : correctIds) {
                    if (!allowedIds.contains(clean(value.asText("")))) {
                        throw new IllegalStateException("Reconstructed key contains an option ID not present in served question " + id + ".");
                    }
                }
            }
        }
        if (seen.size() != paperById.size()) {
            throw new IllegalStateException("Reconstructed key question set is incomplete.");
        }
    }

    private static long epochMicros(Instant now) {
        return now.getEpochSecond() * 1_000_000L + (now.getNano() / 1_000L);
    }

    private static String randomHex(int count) {
        String digits = "0123456789abcdef";
        StringBuilder out = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            out.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        }
        return out.toString();
    }

    private static String randomAlphaNumeric(int count) {
        String digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder out = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            out.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        }
        return out.toString();
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String safeSegment(String value) {
        String clean = clean(value).replaceAll("[^A-Za-z0-9_-]", "_");
        if (clean.isBlank()) {
            throw new IllegalArgumentException("Invalid identifier.");
        }
        return clean;
    }

    private static String slug(String value) {
        String slug = clean(value).replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "exam" : slug;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return clean(value).isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!blank(value)) {
                return clean(value);
            }
        }
        return "";
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private List<SelectedQuestionSummary> readSelectedSummaries(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<SelectedQuestionSummary>>() {
            });
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static SecureRandom secureRandom(String seed) {
        if (seed == null || seed.isBlank()) {
            return SECURE_RANDOM;
        }
        return new SecureRandom(seed.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public record DownloadedAssemblyFile(String fileName, byte[] bytes) {
    }

    private record TopicKey(
            String subject,
            String subjectCode,
            String section,
            String sectionCode,
            String topic,
            String topicCode
    ) {
    }

    private record QBankQuestion(
            String questionId,
            String sourceQuestionId,
            String subject,
            String subjectCode,
            String section,
            String sectionCode,
            String topic,
            String topicCode,
            String difficulty,
            double marks,
            Map<String, String> optionIds,
            ObjectNode node
    ) {
    }

    private record ExportQuestion(
            QBankQuestion source,
            String exportQuestionId,
            String questionNumber,
            Map<String, String> optionIds,
            List<String> optionOrder
    ) {
    }

    private record AssemblyLineageEntry(
            String sourceQuestionId,
            String sourceQuestionKey,
            String exportQuestionId,
            String questionNumber,
            Map<String, String> optionIds,
            List<String> optionOrder
    ) {
    }

    private record PaperAuthMetadata(
            String licenseKey,
            long createdAtMicros,
            String generatedAt,
            String sourcePaperLicenseKey,
            String serverKeyReference
    ) {
    }

    private static final class TopicBucket {
        private final TopicKey key;
        private final Map<String, DifficultyBucket> byDifficulty = new LinkedHashMap<>();
        private int questionCount;
        private double totalMarks;

        private TopicBucket(TopicKey key) {
            this.key = key;
        }

        private void add(QBankQuestion question) {
            questionCount++;
            totalMarks += question.marks();
            byDifficulty.computeIfAbsent(question.difficulty(), DifficultyBucket::new).add(question);
        }

        private TopicInventory toInventory() {
            List<DifficultyInventory> difficulties = byDifficulty.values().stream()
                    .map(DifficultyBucket::toInventory)
                    .sorted(Comparator.comparingInt(item -> difficultyRank(item.difficulty())))
                    .toList();
            return new TopicInventory(
                    key.subject(),
                    key.subjectCode(),
                    key.section(),
                    key.sectionCode(),
                    key.topic(),
                    key.topicCode(),
                    questionCount,
                    round(totalMarks),
                    difficulties
            );
        }
    }

    private static final class DifficultyBucket {
        private final String difficulty;
        private final Map<String, Integer> marks = new LinkedHashMap<>();
        private int questionCount;
        private double totalMarks;

        private DifficultyBucket(String difficulty) {
            this.difficulty = difficulty;
        }

        private void add(QBankQuestion question) {
            questionCount++;
            totalMarks += question.marks();
            String mark = question.marks() == Math.rint(question.marks())
                    ? Integer.toString((int) question.marks())
                    : Double.toString(question.marks());
            marks.put(mark, marks.getOrDefault(mark, 0) + 1);
        }

        private DifficultyInventory toInventory() {
            return new DifficultyInventory(difficulty, questionCount, round(totalMarks), marks);
        }
    }
}
