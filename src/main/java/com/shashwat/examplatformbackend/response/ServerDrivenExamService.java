package com.shashwat.examplatformbackend.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.response.ServerExamDtos.AnswerRequest;
import com.shashwat.examplatformbackend.response.ServerExamDtos.AnswerResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.BootstrapResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.PaperResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionRequest;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.QuestionSummary;
import com.shashwat.examplatformbackend.response.ServerExamDtos.SectionSummary;
import com.shashwat.examplatformbackend.response.ServerExamDtos.StateResponse;
import com.shashwat.examplatformbackend.response.ServerExamDtos.TokenRequest;
import com.shashwat.examplatformbackend.student.ExamAttemptSession;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ServerDrivenExamService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExamAttemptQuestionStateRepository stateRepository;
    private final ExamRepository examRepository;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final ExamAttemptSessionService attemptSessionService;
    private final ExamResponseSubmissionService submissionService;

    public ServerDrivenExamService(
            ExamAttemptQuestionStateRepository stateRepository,
            ExamRepository examRepository,
            QuestionPaperStorageService questionPaperStorageService,
            ExamAttemptSessionService attemptSessionService,
            ExamResponseSubmissionService submissionService
    ) {
        this.stateRepository = stateRepository;
        this.examRepository = examRepository;
        this.questionPaperStorageService = questionPaperStorageService;
        this.attemptSessionService = attemptSessionService;
        this.submissionService = submissionService;
    }

    @Transactional
    public BootstrapResponse bootstrap(TokenRequest request) {
        ExamAttemptSession attempt = attemptSessionService.verifiedAttempt(request.examSessionToken());
        List<ExamAttemptQuestionState> states = ensureStates(attempt);
        return bootstrapResponse(attempt, states);
    }

    @Transactional
    public PaperResponse paper(TokenRequest request) {
        ExamAttemptSession attempt = attemptSessionService.verifiedAttempt(request.examSessionToken());
        List<ExamAttemptQuestionState> states = ensureStates(attempt);
        BootstrapResponse bootstrap = bootstrapResponse(attempt, states);
        List<QuestionResponse> paper = states.stream()
                .map(this::questionResponse)
                .toList();
        return new PaperResponse(
                bootstrap.examCode(),
                bootstrap.registrationId(),
                bootstrap.questionCount(),
                bootstrap.dueAt(),
                bootstrap.sections(),
                bootstrap.questions(),
                paper
        );
    }

    @Transactional
    public QuestionResponse question(QuestionRequest request) {
        ExamAttemptSession attempt = attemptSessionService.verifiedAttempt(request.examSessionToken());
        ensureStates(attempt);
        ExamAttemptQuestionState state = stateRepository.findByAttemptIdAndDisplayIndex(attempt.getId(), request.displayIndex())
                .orElseThrow(() -> new IllegalArgumentException("Question is not available in this exam session."));
        JsonNode question = questionJson(state);
        normalizeStoredState(state, question);
        if ("not_visited".equals(state.getStatus())) {
            state.recordAnswer(state.getAnswerText(), state.getMarkedOptionIdsJson(), "not_answered",
                    state.isMarkedForReview(), state.getTimeSpentMs(), state.getActivityJson());
        }
        return questionResponse(state, question);
    }

    @Transactional
    public AnswerResponse answer(AnswerRequest request) {
        ExamAttemptSession attempt = attemptSessionService.verifiedAttempt(request.examSessionToken());
        ensureStates(attempt);
        ExamAttemptQuestionState state = stateRepository.findByAttemptIdAndDisplayIndex(attempt.getId(), request.displayIndex())
                .orElseThrow(() -> new IllegalArgumentException("Question is not available in this exam session."));
        JsonNode question = questionJson(state);
        NormalizedAnswer normalized = normalizeAnswer(question, request.markedOptionIds(), request.natAnswer(), request.status(), request.markedForReview());
        String markedOptionIdsJson = toJson(normalized.markedOptionIds());
        String activityJson = toJson(request.activity() == null ? Map.of() : request.activity());
        state.recordAnswer(normalized.natAnswer(), markedOptionIdsJson, normalized.status(), normalized.markedForReview(),
                request.timeSpentMs(), activityJson);
        List<ExamAttemptQuestionState> states = stateRepository.findAllByAttemptIdOrderByDisplayIndex(attempt.getId());
        return new AnswerResponse(displayStateResponse(state, question), counts(states));
    }

    @Transactional
    public ExamSubmitResponse submit(TokenRequest request) {
        ExamAttemptSession attempt = attemptSessionService.verifiedAttempt(request.examSessionToken());
        List<ExamAttemptQuestionState> states = ensureStates(attempt);
        states.forEach(state -> normalizeStoredState(state, questionJson(state)));
        List<ExamAnswerRequest> answers = states.stream()
                .map(this::answerFromState)
                .toList();
        return submissionService.submit(new ExamSubmitRequest(
                attempt.getRegistrationId(),
                null,
                request.examSessionToken(),
                answers
        ));
    }

    private List<ExamAttemptQuestionState> ensureStates(ExamAttemptSession attempt) {
        List<ExamAttemptQuestionState> existing = stateRepository.findAllByAttemptIdOrderByDisplayIndex(attempt.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        Exam exam = examRepository.findByExamCode(attempt.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam was not found."));
        List<QuestionSeed> seeds = questionSeeds(exam);
        if (seeds.isEmpty()) {
            throw new IllegalStateException("Question paper has no usable questions.");
        }
        List<QuestionSeed> ordered = shuffledBySection(seeds, attempt);
        List<ExamAttemptQuestionState> created = new ArrayList<>();
        int displayIndex = 0;
        for (QuestionSeed seed : ordered) {
            displayIndex++;
            String displayQuestionId = "Q." + displayIndex;
            created.add(new ExamAttemptQuestionState(
                    attempt.getId(),
                    attempt.getExamCode(),
                    attempt.getRegistrationId(),
                    displayIndex,
                    seed.sectionName(),
                    seed.sectionIndex(),
                    seed.sourceIndex(),
                    displayQuestionId,
                    seed.questionId(),
                    seed.questionJson()
            ));
        }
        return stateRepository.saveAll(created);
    }

    private List<QuestionSeed> shuffledBySection(List<QuestionSeed> seeds, ExamAttemptSession attempt) {
        Map<String, List<QuestionSeed>> bySection = new LinkedHashMap<>();
        for (QuestionSeed seed : seeds) {
            bySection.computeIfAbsent(seed.sectionName(), ignored -> new ArrayList<>()).add(seed);
        }
        SecureRandom random = seededRandom(attempt);
        List<QuestionSeed> ordered = new ArrayList<>();
        for (List<QuestionSeed> sectionSeeds : bySection.values()) {
            List<QuestionSeed> copy = new ArrayList<>(sectionSeeds);
            java.util.Collections.shuffle(copy, random);
            ordered.addAll(copy);
        }
        return ordered;
    }

    private List<QuestionSeed> questionSeeds(Exam exam) {
        JsonNode root = questionPaperStorageService.sanitizedPaper(exam);
        JsonNode questions = root.get("questions");
        if (!(questions instanceof ArrayNode arrayNode)) {
            return List.of();
        }
        List<QuestionSeed> seeds = new ArrayList<>();
        Map<String, Integer> sectionIndexes = new LinkedHashMap<>();
        int sourceIndex = 0;
        for (JsonNode question : arrayNode) {
            sourceIndex++;
            if (!question.isObject()) {
                continue;
            }
            String section = firstText(question, "section", "section_name", "sectionName", "subject", "subject_name", "subjectName");
            if (section.isBlank()) {
                section = "Section 1";
            }
            int sectionIndex = sectionIndexes.computeIfAbsent(section, ignored -> sectionIndexes.size());
            String questionId = firstText(question, "question_id", "questionId", "bank_question_id", "bankQuestionId", "qid", "id");
            if (questionId.isBlank()) {
                questionId = "SOURCE-" + sourceIndex;
            }
            seeds.add(new QuestionSeed(section, sectionIndex, sourceIndex, questionId, question.toString()));
        }
        return seeds;
    }

    private BootstrapResponse bootstrapResponse(ExamAttemptSession attempt, List<ExamAttemptQuestionState> states) {
        List<SectionSummary> sections = new ArrayList<>();
        String currentSection = "";
        int sectionStart = 1;
        int count = 0;
        for (ExamAttemptQuestionState state : states) {
            if (!state.getSectionName().equals(currentSection)) {
                if (!currentSection.isBlank()) {
                    sections.add(new SectionSummary(currentSection, sectionStart, count));
                }
                currentSection = state.getSectionName();
                sectionStart = state.getDisplayIndex();
                count = 0;
            }
            count++;
        }
        if (!currentSection.isBlank()) {
            sections.add(new SectionSummary(currentSection, sectionStart, count));
        }
        List<QuestionSummary> questions = states.stream()
                .map(state -> new QuestionSummary(state.getDisplayIndex(), state.getDisplayQuestionId(),
                        state.getSectionName(), state.getStatus(), state.isMarkedForReview()))
                .toList();
        return new BootstrapResponse(attempt.getExamCode(), attempt.getRegistrationId(), states.size(),
                attempt.getDueAt(), sections, questions);
    }

    private QuestionResponse questionResponse(ExamAttemptQuestionState state) {
        return questionResponse(state, questionJson(state));
    }

    private QuestionResponse questionResponse(ExamAttemptQuestionState state, JsonNode question) {
        return new QuestionResponse(
                state.getDisplayIndex(),
                state.getDisplayQuestionId(),
                state.getSectionName(),
                state.getQuestionId(),
                OBJECT_MAPPER.convertValue(question, Object.class),
                displayStateResponse(state, question)
        );
    }

    private Object questionPayload(ExamAttemptQuestionState state) {
        return OBJECT_MAPPER.convertValue(questionJson(state), Object.class);
    }

    private ExamAnswerRequest answerFromState(ExamAttemptQuestionState state) {
        JsonNode question = questionJson(state);
        normalizeStoredState(state, question);
        return new ExamAnswerRequest(
                state.getSectionName(),
                state.getQuestionId(),
                state.getDisplayQuestionId(),
                firstText(question, "question_type", "questionType", "type"),
                parseStringList(state.getMarkedOptionIdsJson()),
                state.getAnswerText() == null ? "" : state.getAnswerText(),
                state.getStatus(),
                state.isMarkedForReview(),
                parseObject(state.getActivityJson()),
                firstText(question,
                        "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image",
                        "questionDisplayImage", "question_display_image", "displayImage", "display_image",
                        "questionBaseImage", "question_base_image", "baseImage", "base_image",
                        "question_image", "questionImage", "image"),
                firstText(question, "question_text", "questionText", "body", "text", "question"),
                question.get("options")
        );
    }

    private JsonNode questionJson(ExamAttemptQuestionState state) {
        // The attempt row is the authority for display order and response state.
        // The uploaded paper file is the authority for renderable question/option
        // media. This avoids stale/broken LOB snapshots from ever blanking the UI.
        JsonNode question = recoverQuestionFromPaper(state);
        Exam exam = examRepository.findByExamCode(state.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam was not found."));
        return exam.isOptionRandomizationEnabled() ? randomizedOptionsQuestion(question, state) : question;
    }

    private JsonNode recoverQuestionFromPaper(ExamAttemptQuestionState state) {
        Exam exam = examRepository.findByExamCode(state.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam was not found."));
        JsonNode root = questionPaperStorageService.sanitizedPaper(exam);
        JsonNode questions = root.get("questions");
        if (questions instanceof ArrayNode arrayNode) {
            int sourceIndex = state.getSourceIndex() - 1;
            if (sourceIndex >= 0 && sourceIndex < arrayNode.size() && arrayNode.get(sourceIndex).isObject()) {
                return arrayNode.get(sourceIndex);
            }
        }
        throw new IllegalStateException("Stored question snapshot is invalid.");
    }

    private StateResponse stateResponse(ExamAttemptQuestionState state, JsonNode question) {
        normalizeStoredState(state, question);
        return new StateResponse(
                state.getStatus(),
                state.isMarkedForReview(),
                parseStringList(state.getMarkedOptionIdsJson()),
                state.getAnswerText() == null ? "" : state.getAnswerText(),
                state.getTimeSpentMs(),
                parseObject(state.getActivityJson()),
                state.getUpdatedAt()
        );
    }

    private StateResponse displayStateResponse(ExamAttemptQuestionState state, JsonNode question) {
        StateResponse stored = stateResponse(state, question);
        return new StateResponse(
                stored.status(),
                stored.markedForReview(),
                displayMarkedOptionIds(question, stored.markedOptionIds()),
                stored.natAnswer(),
                stored.timeSpentMs(),
                stored.activity(),
                stored.updatedAt()
        );
    }

    private void normalizeStoredState(ExamAttemptQuestionState state, JsonNode question) {
        NormalizedAnswer normalized = normalizeAnswer(
                question,
                parseStringList(state.getMarkedOptionIdsJson()),
                state.getAnswerText(),
                state.getStatus(),
                state.isMarkedForReview()
        );
        String currentNat = state.getAnswerText() == null ? "" : state.getAnswerText();
        List<String> currentMarked = parseStringList(state.getMarkedOptionIdsJson());
        if (currentNat.equals(normalized.natAnswer())
                && currentMarked.equals(normalized.markedOptionIds())
                && state.getStatus().equals(normalized.status())
                && state.isMarkedForReview() == normalized.markedForReview()) {
            return;
        }
        state.recordAnswer(
                normalized.natAnswer(),
                toJson(normalized.markedOptionIds()),
                normalized.status(),
                normalized.markedForReview(),
                state.getTimeSpentMs(),
                state.getActivityJson()
        );
    }

    private static NormalizedAnswer normalizeAnswer(
            JsonNode question,
            List<String> markedOptionIds,
            String natAnswer,
            String status,
            boolean markedForReview
    ) {
        String safeStatus = safeStatus(status);
        boolean nat = isNatQuestion(question);
        List<String> cleanedMarked = nat ? List.of() : sanitizeMarkedOptionIds(question, markedOptionIds);
        String cleanedNat = nat ? normalizeNatAnswer(natAnswer) : "";
        boolean answered = nat ? !cleanedNat.isBlank() : !cleanedMarked.isEmpty();
        boolean review = markedForReview || "review".equals(safeStatus) || "review_marked".equals(safeStatus);
        String normalizedStatus;
        if ("not_visited".equals(safeStatus) && !answered && !review) {
            normalizedStatus = "not_visited";
        } else if (review) {
            normalizedStatus = answered ? "review_marked" : "review";
        } else {
            normalizedStatus = answered ? "answered" : "not_answered";
        }
        return new NormalizedAnswer(cleanedMarked, cleanedNat, normalizedStatus, review);
    }

    private static boolean isNatQuestion(JsonNode question) {
        String type = normalizedQuestionType(question);
        return type.contains("NAT") || type.contains("NUM");
    }

    private static boolean isMsqQuestion(JsonNode question) {
        String type = normalizedQuestionType(question);
        if (type.contains("MSQ")
                || type.contains("MULTI SELECT")
                || type.contains("MULTISELECT")
                || type.contains("MULTIPLE SELECT")
                || type.contains("MULTIPLE ANSWER")
                || type.contains("SELECT ALL")) {
            return true;
        }
        return hasTruthy(question, "multiple", "multiSelect", "multipleAnswers", "multiple_answers", "allowMultiple", "allow_multiple")
                || hasNumericGreaterThan(question, 1, "maxSelections", "max_selections", "maximumSelections", "maximum_selections")
                || hasArrayWithMultipleValues(question,
                        "correct_options", "correctOptions",
                        "correct_option_ids", "correctOptionIds",
                        "answers", "answerOptions", "answer_options");
    }

    private static String normalizedQuestionType(JsonNode question) {
        String type = firstText(question,
                "question_type", "questionType", "type",
                "answer_type", "answerType",
                "question_nature", "questionNature",
                "response_type", "responseType",
                "selection_type", "selectionType")
                .toUpperCase()
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (type.isBlank() || "MCQ".equals(type) || "QUESTION".equals(type)) {
            String nestedType = nestedFirstText(question,
                    List.of("selection_meta", "selectionMeta", "metadata", "meta"),
                    List.of("question_type", "questionType", "type", "answer_type", "answerType", "selection_type", "selectionType"))
                    .toUpperCase()
                    .replace('_', ' ')
                    .replace('-', ' ')
                    .trim();
            if (!nestedType.isBlank()) {
                return nestedType;
            }
        }
        return type;
    }

    private static String normalizeNatAnswer(String natAnswer) {
        return natAnswer == null ? "" : natAnswer.trim();
    }

    private static List<String> sanitizeMarkedOptionIds(JsonNode question, List<String> markedOptionIds) {
        if (markedOptionIds == null || markedOptionIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayToOriginal = displayOptionIdMap(question);
        Set<String> allowed = allowedOptionTokens(question);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : markedOptionIds) {
            String clean = value == null ? "" : value.trim();
            if (clean.isBlank()) {
                continue;
            }
            String upper = clean.toUpperCase();
            if (displayToOriginal.containsKey(upper)) {
                normalized.add(displayToOriginal.get(upper));
                continue;
            }
            String sourceId = sourceOptionIdForDisplayLetter(question, upper);
            if (!sourceId.isBlank()) {
                normalized.add(sourceId);
                continue;
            }
            if (allowed.contains(upper)) {
                normalized.add(upper);
                continue;
            }
            if (allowed.contains(clean)) {
                normalized.add(clean);
            }
        }
        return List.copyOf(normalized);
    }

    private static String sourceOptionIdForDisplayLetter(JsonNode question, String displayLetter) {
        if (displayLetter == null || displayLetter.length() != 1 || !Character.isLetter(displayLetter.charAt(0))) {
            return "";
        }
        LinkedHashMap<String, JsonNode> options = optionNodesBySourceLetter(question);
        if (options.isEmpty()) {
            return "";
        }
        LinkedHashMap<String, String> sourceIds = sourceOptionIdMap(question, options);
        return sourceIds.getOrDefault(displayLetter.toUpperCase(), "");
    }

    private static List<String> displayMarkedOptionIds(JsonNode question, List<String> storedOptionIds) {
        if (storedOptionIds == null || storedOptionIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayToOriginal = displayOptionIdMap(question);
        Map<String, String> originalToDisplay = new LinkedHashMap<>();
        displayToOriginal.forEach((display, original) -> originalToDisplay.putIfAbsent(original, display));
        List<String> out = new ArrayList<>();
        for (String value : storedOptionIds) {
            String clean = value == null ? "" : value.trim();
            if (clean.isBlank()) {
                continue;
            }
            out.add(originalToDisplay.getOrDefault(clean, clean));
        }
        return out;
    }

    private static Map<String, String> displayOptionIdMap(JsonNode question) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        JsonNode optionIds = question.get("option_ids");
        if (optionIds != null && optionIds.isObject()) {
            optionIds.fields().forEachRemaining(entry -> {
                String display = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase();
                String original = entry.getValue() == null ? "" : entry.getValue().asText("").trim();
                if (!display.isBlank() && !original.isBlank()) {
                    out.put(display, original);
                }
            });
        }
        return out;
    }

    private static boolean hasTruthy(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean() && value.asBoolean()) {
                return true;
            }
            if (value.isTextual() && Boolean.parseBoolean(value.asText("").trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNumericGreaterThan(JsonNode node, int threshold, String... keys) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber() && value.asInt() > threshold) {
                return true;
            }
            if (value.isTextual()) {
                try {
                    if (Integer.parseInt(value.asText("").trim()) > threshold) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a numeric hint.
                }
            }
        }
        return false;
    }

    private static boolean hasArrayWithMultipleValues(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isArray()) {
                int count = 0;
                for (JsonNode item : value) {
                    if (item != null && !item.asText("").trim().isBlank()) {
                        count++;
                    }
                    if (count > 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String nestedFirstText(JsonNode node, List<String> parentKeys, List<String> childKeys) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String parentKey : parentKeys) {
            JsonNode parent = node.get(parentKey);
            if (parent == null || !parent.isObject()) {
                continue;
            }
            String value = firstText(parent, childKeys.toArray(String[]::new));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static JsonNode randomizedOptionsQuestion(JsonNode question, ExamAttemptQuestionState state) {
        if (!(question instanceof ObjectNode source) || isNatQuestion(question) || isMsqQuestion(question)) {
            return question;
        }
        LinkedHashMap<String, JsonNode> optionBySourceLetter = optionNodesBySourceLetter(question);
        if (optionBySourceLetter.size() <= 1) {
            return question;
        }
        LinkedHashMap<String, String> sourceOptionIds = sourceOptionIdMap(question, optionBySourceLetter);
        List<String> sourceLetters = new ArrayList<>(optionBySourceLetter.keySet());
        SecureRandom random = seededRandom(
                state.getExamCode() + "|" + state.getRegistrationId() + "|" + state.getQuestionId() + "|" + state.getSourceIndex() + "|option"
        );
        java.util.Collections.shuffle(sourceLetters, random);
        ObjectNode copy = source.deepCopy();
        boolean sourceWasArray = question.path("options").isArray();
        ObjectNode shuffledObject = OBJECT_MAPPER.createObjectNode();
        ArrayNode shuffledArray = OBJECT_MAPPER.createArrayNode();
        ObjectNode displayOptionIds = OBJECT_MAPPER.createObjectNode();
        for (int index = 0; index < sourceLetters.size(); index++) {
            String displayLetter = String.valueOf((char) ('A' + index));
            String sourceLetter = sourceLetters.get(index);
            JsonNode option = optionBySourceLetter.get(sourceLetter);
            if (sourceWasArray) {
                shuffledArray.add(option);
            } else {
                shuffledObject.set(displayLetter, option);
            }
            displayOptionIds.put(displayLetter, sourceOptionIds.getOrDefault(sourceLetter, sourceLetter));
        }
        copy.set("options", sourceWasArray ? shuffledArray : shuffledObject);
        copy.set("option_ids", displayOptionIds);
        copy.put("options_randomized", true);
        return copy;
    }

    private static LinkedHashMap<String, JsonNode> optionNodesBySourceLetter(JsonNode question) {
        LinkedHashMap<String, JsonNode> out = new LinkedHashMap<>();
        JsonNode options = question.get("options");
        if (options == null || options.isNull()) {
            return out;
        }
        if (options.isArray()) {
            for (int index = 0; index < options.size(); index++) {
                out.put(String.valueOf((char) ('A' + index)), options.get(index));
            }
        } else if (options.isObject()) {
            options.fields().forEachRemaining(entry -> {
                String letter = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase();
                if (letter.length() == 1 && Character.isLetter(letter.charAt(0))) {
                    out.put(letter, entry.getValue());
                }
            });
        }
        return out;
    }

    private static LinkedHashMap<String, String> sourceOptionIdMap(JsonNode question, LinkedHashMap<String, JsonNode> optionBySourceLetter) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        JsonNode optionIds = question.get("option_ids");
        if (optionIds != null && optionIds.isObject()) {
            optionIds.fields().forEachRemaining(entry -> {
                String letter = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase();
                String id = entry.getValue() == null ? "" : entry.getValue().asText("").trim();
                if (!letter.isBlank() && !id.isBlank()) {
                    out.put(letter, id);
                }
            });
        }
        optionBySourceLetter.forEach((letter, option) -> {
            if (out.containsKey(letter)) {
                return;
            }
            String id = firstText(option, "id", "optionId", "option_id", "oid", "key", "name");
            out.put(letter, id.isBlank() ? letter : id);
        });
        return out;
    }

    private static Set<String> allowedOptionTokens(JsonNode question) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        JsonNode options = question.get("options");
        if (options != null) {
            if (options.isArray()) {
                int index = 0;
                for (JsonNode option : options) {
                    index++;
                    String fallbackLabel = String.valueOf((char) ('A' + index - 1));
                    allowed.add(fallbackLabel);
                    String id = firstText(option, "id", "optionId", "option_id", "key", "name");
                    if (!id.isBlank()) {
                        allowed.add(id);
                    }
                    String label = firstText(option, "label", "key", "name");
                    if (!label.isBlank() && label.length() == 1 && Character.isLetter(label.charAt(0))) {
                        allowed.add(label.toUpperCase());
                    }
                }
            } else if (options.isObject()) {
                options.fieldNames().forEachRemaining(field -> {
                    if (field != null && field.length() == 1 && Character.isLetter(field.charAt(0))) {
                        allowed.add(field.toUpperCase());
                    }
                });
            }
        }
        JsonNode optionIds = question.get("option_ids");
        if (optionIds != null && optionIds.isObject()) {
            optionIds.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (key != null && key.length() == 1 && Character.isLetter(key.charAt(0))) {
                    allowed.add(key.toUpperCase());
                }
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual()) {
                    String id = value.asText("").trim();
                    if (!id.isBlank()) {
                        allowed.add(id);
                    }
                }
            });
        }
        return allowed;
    }

    private Map<String, Long> counts(List<ExamAttemptQuestionState> states) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("answered", 0L);
        counts.put("not_answered", 0L);
        counts.put("not_visited", 0L);
        counts.put("review", 0L);
        counts.put("review_marked", 0L);
        for (ExamAttemptQuestionState state : states) {
            counts.compute(state.getStatus(), (ignored, value) -> value == null ? 1L : value + 1L);
        }
        return counts;
    }

    private static String safeStatus(String status) {
        String value = status == null ? "" : status.trim();
        return switch (value) {
            case "answered", "not_answered", "not_visited", "review", "review_marked" -> value;
            default -> "not_answered";
        };
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static SecureRandom seededRandom(ExamAttemptSession attempt) {
        byte[] seed = sha256(attempt.getExamCode() + ":" + attempt.getRegistrationId() + ":" + attempt.getId());
        return new SecureRandom(seed);
    }

    private static SecureRandom seededRandom(String seedText) {
        return new SecureRandom(sha256(seedText == null ? "" : seedText));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Secure question shuffle is not available.");
        }
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not save exam state.");
        }
    }

    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
            return values;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static Object parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private record QuestionSeed(String sectionName, int sectionIndex, int sourceIndex, String questionId, String questionJson) {
    }

    private record NormalizedAnswer(List<String> markedOptionIds, String natAnswer, String status, boolean markedForReview) {
    }
}
