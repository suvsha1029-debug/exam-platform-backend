package com.shashwat.examplatformbackend.evaluation;

import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.response.ExamResponseSubmission;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.response.ExamSubmissionResponse;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SyllabusAnalysisService {
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(?:\\d{1,2}|[A-Z])(?:[.)]|\\s+-\\s+)\\s+.+");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "for", "from", "if", "in", "into", "is",
            "it", "of", "on", "or", "that", "the", "their", "then", "there", "these", "this", "to", "with",
            "which", "where", "will", "given", "following", "shown", "figure", "value", "values", "correct",
            "question", "option", "options", "find", "determine", "consider", "using"
    );
    private static final List<String> EC_FALLBACK_TOPICS = List.of(
            "Engineering Mathematics",
            "Networks, Signals and Systems",
            "Electronic Devices",
            "Analog Circuits",
            "Digital Circuits",
            "Control Systems",
            "Communications",
            "Electromagnetics",
            "General Aptitude"
    );

    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final ExamResponseSubmissionRepository submissionRepository;
    private final EvaluationService evaluationService;

    public SyllabusAnalysisService(StudentRepository studentRepository,
                                   ExamRepository examRepository,
                                   ExamResponseSubmissionRepository submissionRepository,
                                   EvaluationService evaluationService) {
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationService = evaluationService;
    }

    @Transactional
    public SyllabusAnalysisResponse analyzeCandidate(String registrationId, MultipartFile syllabusPdf) {
        String normalized = text(registrationId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Registration ID is required.");
        }
        if (syllabusPdf == null || syllabusPdf.isEmpty()) {
            throw new IllegalArgumentException("Upload a syllabus PDF first.");
        }
        Student student = studentRepository.findByRegistrationId(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found."));
        student.requireResultAvailable();
        Exam exam = examRepository.findByExamCode(student.getExamCode())
                .orElseThrow(() -> new IllegalStateException("Linked exam not found."));
        if (!exam.isResultReleased()) {
            throw new IllegalStateException("Result card is not released by admin yet.");
        }
        ExamResponseSubmission submission = submissionRepository.findByExamCodeAndRegistrationId(exam.getExamCode(), student.getRegistrationId())
                .orElseThrow(() -> new IllegalStateException("No evaluated response found for this candidate."));
        EvaluationResultResponse evaluated = evaluationService.responseResult(exam.getExamCode(), submission.getId());
        List<Map<String, Object>> answers = ExamSubmissionResponse.parseAnswers(submission.getAnswersJson());
        List<SyllabusTopic> topics = topics(syllabusPdf);
        List<SyllabusQuestionMappingResponse> questionRows = new ArrayList<>();
        Map<String, TopicAccumulator> totals = new LinkedHashMap<>();
        for (SyllabusTopic topic : topics) {
            totals.put(topic.title(), new TopicAccumulator(topic.title()));
        }
        totals.putIfAbsent("Unmapped", new TopicAccumulator("Unmapped"));

        List<EvaluatedQuestionResponse> evaluatedQuestions = evaluated.questions() == null ? List.of() : evaluated.questions();
        for (int index = 0; index < evaluatedQuestions.size(); index++) {
            EvaluatedQuestionResponse question = evaluatedQuestions.get(index);
            Map<String, Object> answer = index < answers.size() ? answers.get(index) : Map.of();
            Classification classification = classify(answer, question, topics);
            String topicName = classification.confidence() >= 0.16 ? classification.topic() : "Unmapped";
            TopicAccumulator accumulator = totals.computeIfAbsent(topicName, TopicAccumulator::new);
            accumulator.accept(question, classification.confidence());
            questionRows.add(new SyllabusQuestionMappingResponse(
                    question.displayQuestionId(),
                    question.questionId(),
                    question.type(),
                    topicName,
                    question.attempted(),
                    question.correctAnswer(),
                    round(question.awardedMarks()),
                    round(question.marks()),
                    round(classification.confidence()),
                    classification.evidence()
            ));
        }

        List<SyllabusTopicScoreResponse> topicRows = totals.values()
                .stream()
                .filter(row -> row.questionCount > 0)
                .sorted(Comparator.comparing((TopicAccumulator row) -> "Unmapped".equals(row.topic)).thenComparing(row -> row.topic.toLowerCase(Locale.ROOT)))
                .map(TopicAccumulator::response)
                .toList();
        int mapped = (int) questionRows.stream().filter(row -> !"Unmapped".equals(row.topic())).count();
        return new SyllabusAnalysisResponse(
                exam.getExamCode(),
                exam.getName(),
                student.getRegistrationId(),
                student.getFullName(),
                cleanFileName(syllabusPdf.getOriginalFilename()),
                topics.size(),
                mapped,
                Math.max(0, questionRows.size() - mapped),
                evaluated.score(),
                evaluated.maxMarks(),
                topicRows,
                questionRows
        );
    }

    private List<SyllabusTopic> topics(MultipartFile file) {
        String extracted = extractText(file);
        List<SyllabusTopic> parsed = parseTopics(extracted);
        if (parsed.size() >= 4) {
            return parsed;
        }
        List<SyllabusTopic> fallback = new ArrayList<>();
        for (String topic : EC_FALLBACK_TOPICS) {
            fallback.add(new SyllabusTopic(topic, topic + " " + synonyms(topic), vector(topic + " " + synonyms(topic))));
        }
        return fallback;
    }

    private String extractText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read syllabus PDF.");
        }
    }

    private List<SyllabusTopic> parseTopics(String rawText) {
        String[] lines = rawText == null ? new String[0] : rawText.replace('\u00a0', ' ').split("\\R+");
        List<TopicBlock> blocks = new ArrayList<>();
        TopicBlock current = null;
        for (String raw : lines) {
            String line = normalizeLine(raw);
            if (line.isBlank() || ignoreLine(line)) {
                continue;
            }
            if (looksLikeHeading(line)) {
                current = new TopicBlock(cleanHeading(line));
                blocks.add(current);
            } else if (current != null) {
                current.body.append(' ').append(line);
            }
        }
        if (blocks.size() < 4) {
            blocks = knownGateBlocks(rawText == null ? "" : rawText);
        }
        List<SyllabusTopic> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TopicBlock block : blocks) {
            String title = block.title.trim();
            if (title.length() < 3 || !seen.add(title.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String document = title + " " + block.body + " " + synonyms(title);
            out.add(new SyllabusTopic(title, document, vector(document)));
        }
        return out;
    }

    private List<TopicBlock> knownGateBlocks(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<TopicBlock> blocks = new ArrayList<>();
        for (String title : EC_FALLBACK_TOPICS) {
            if (lower.contains(title.toLowerCase(Locale.ROOT))) {
                blocks.add(new TopicBlock(title));
            }
        }
        return blocks;
    }

    private Classification classify(Map<String, Object> answer, EvaluatedQuestionResponse question, List<SyllabusTopic> topics) {
        String document = question.section() + " " + question.displayQuestionId() + " " + question.type() + " "
                + text(answer.get("body")) + " "
                + text(answer.get("questionText")) + " "
                + optionText(answer.get("options")) + " "
                + synonyms(text(answer.get("section")));
        Map<String, Double> qVector = vector(document);
        if (qVector.isEmpty()) {
            return new Classification("Unmapped", 0, "");
        }
        SyllabusTopic best = null;
        double bestScore = 0;
        for (SyllabusTopic topic : topics) {
            double score = cosine(qVector, topic.vector());
            score += titleBoost(document, topic.title());
            if (score > bestScore) {
                bestScore = score;
                best = topic;
            }
        }
        if (best == null) {
            return new Classification("Unmapped", 0, "");
        }
        return new Classification(best.title(), Math.min(1, bestScore), evidence(qVector, best.vector()));
    }

    private static Map<String, Double> vector(String text) {
        Map<String, Double> out = new HashMap<>();
        var matcher = WORD.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = stem(matcher.group());
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            double weight = token.matches("\\d+") ? 0.35 : 1.0;
            out.merge(token, weight, Double::sum);
        }
        return out;
    }

    private static double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        double dot = 0;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            dot += entry.getValue() * right.getOrDefault(entry.getKey(), 0.0);
        }
        return dot / (norm(left) * norm(right));
    }

    private static double norm(Map<String, Double> vector) {
        double sum = 0;
        for (double value : vector.values()) {
            sum += value * value;
        }
        return Math.sqrt(Math.max(sum, 0.000001));
    }

    private static double titleBoost(String document, String title) {
        String lower = document.toLowerCase(Locale.ROOT);
        double boost = 0;
        for (String token : tokenize(title + " " + synonyms(title))) {
            if (lower.contains(token)) {
                boost += 0.025;
            }
        }
        return Math.min(0.18, boost);
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        var matcher = WORD.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            out.add(stem(matcher.group()));
        }
        return out;
    }

    private static String evidence(Map<String, Double> question, Map<String, Double> topic) {
        return question.keySet()
                .stream()
                .filter(topic::containsKey)
                .filter(token -> !STOPWORDS.contains(token))
                .limit(8)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String optionText(Object options) {
        if (options instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder();
            for (Object value : map.values()) {
                out.append(' ').append(optionValue(value));
            }
            return out.toString();
        }
        return text(options);
    }

    private static String optionValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return text(first(map, "text", "label", "body", "optionText", "value"));
        }
        return text(value);
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !text(value).isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String synonyms(String title) {
        String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (lower.contains("digital")) return "boolean logic gate flip flop kmap k-map combinational sequential counter register adc dac mux decoder encoder";
        if (lower.contains("network")) return "circuit laplace impedance admittance transfer function two port rlc transient steady state signal system fourier z transform";
        if (lower.contains("analog")) return "op amp operational amplifier diode transistor mosfet bjt amplifier feedback oscillator filter";
        if (lower.contains("device")) return "semiconductor diode mos capacitor carrier pn junction band mobility";
        if (lower.contains("control")) return "feedback stability root locus bode nyquist controller compensator transfer function poles zeros";
        if (lower.contains("communication")) return "am fm pm modulation demodulation noise information channel coding sampling antenna";
        if (lower.contains("electromagnetic")) return "maxwell waveguide transmission line electric magnetic field propagation boundary";
        if (lower.contains("mathematics")) return "linear algebra calculus differential equation probability random variable matrix vector integral";
        if (lower.contains("aptitude")) return "verbal numerical reasoning grammar word arithmetic percentage ratio";
        return "";
    }

    private static boolean looksLikeHeading(String line) {
        String clean = cleanHeading(line);
        if (clean.length() < 4 || clean.length() > 90) {
            return false;
        }
        if (NUMBERED_HEADING.matcher(line).matches()) {
            return true;
        }
        if (EC_FALLBACK_TOPICS.stream().anyMatch(topic -> topic.equalsIgnoreCase(clean))) {
            return true;
        }
        long commas = clean.chars().filter(ch -> ch == ',').count();
        long words = clean.split("\\s+").length;
        return words <= 6 && commas == 0 && Character.isUpperCase(clean.charAt(0)) && !clean.endsWith(".");
    }

    private static boolean ignoreLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("graduate aptitude test")
                || lower.equals("syllabus")
                || lower.startsWith("page ")
                || lower.startsWith("©");
    }

    private static String normalizeLine(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanHeading(String line) {
        return normalizeLine(line)
                .replaceFirst("^(?:\\d{1,2}|[A-Z])(?:[.)]|\\s+-\\s+)\\s*", "")
                .replaceFirst("^Section\\s+\\d+\\s*[:.-]\\s*", "")
                .trim();
    }

    private static String stem(String token) {
        String out = token.toLowerCase(Locale.ROOT);
        if (out.length() > 5 && out.endsWith("ing")) out = out.substring(0, out.length() - 3);
        if (out.length() > 4 && out.endsWith("ed")) out = out.substring(0, out.length() - 2);
        if (out.length() > 4 && out.endsWith("s")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String cleanFileName(String value) {
        String name = text(value);
        return name.isBlank() ? "syllabus.pdf" : name;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record SyllabusTopic(String title, String document, Map<String, Double> vector) {
    }

    private record Classification(String topic, double confidence, String evidence) {
    }

    private static final class TopicBlock {
        private final String title;
        private final StringBuilder body = new StringBuilder();

        private TopicBlock(String title) {
            this.title = title;
        }
    }

    private static final class TopicAccumulator {
        private final String topic;
        private int questionCount;
        private int attemptedCount;
        private int correctCount;
        private int wrongCount;
        private int blankCount;
        private double awardedMarks;
        private double maxMarks;
        private double confidenceSum;

        private TopicAccumulator(String topic) {
            this.topic = topic;
        }

        private void accept(EvaluatedQuestionResponse question, double confidence) {
            questionCount++;
            if (question.attempted()) {
                attemptedCount++;
                if (question.correctAnswer()) {
                    correctCount++;
                } else {
                    wrongCount++;
                }
            } else {
                blankCount++;
            }
            awardedMarks += question.awardedMarks();
            maxMarks += question.marks();
            confidenceSum += confidence;
        }

        private SyllabusTopicScoreResponse response() {
            double accuracy = attemptedCount == 0 ? 0 : (correctCount * 100.0 / attemptedCount);
            return new SyllabusTopicScoreResponse(
                    topic,
                    questionCount,
                    attemptedCount,
                    correctCount,
                    wrongCount,
                    blankCount,
                    round(awardedMarks),
                    round(maxMarks),
                    round(accuracy),
                    round(questionCount == 0 ? 0 : confidenceSum / questionCount)
            );
        }
    }
}
