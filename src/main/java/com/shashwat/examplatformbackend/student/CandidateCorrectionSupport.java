package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.exam.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.*;

public class CandidateCorrectionSupport {
    static final Map<String, String> FILE_FIELDS = Map.of(
            "photo", "PHOTO", "signature", "SIGNATURE", "idCard", "ID_CARD",
            "class10Marksheet", "CLASS_10_MARKSHEET", "class12Marksheet", "CLASS_12_MARKSHEET",
            "ugDocument", "UG_DETAILS", "pgDocument", "PG_DETAILS");
    private final CandidateFileStorageService storage;

    public CandidateCorrectionSupport(CandidateFileStorageService storage) { this.storage = storage; }

    static Map<String, String> permissions(Exam exam) {
        Map<String, String> allowed = new LinkedHashMap<>();
        CustomCandidateFieldJson.parseStringMap(exam.getCandidateCorrectionFieldsJson()).forEach((key, value) -> {
            if ("true".equalsIgnoreCase(value)) allowed.put(key, "true");
        });
        for (var field : CustomCandidateFieldJson.parse(exam.getCustomFieldsJson())) {
            if (field.correctionAllowed()) allowed.put("customField_" + field.key(), "true");
        }
        return allowed;
    }

    static Map<String, String> files(Student student) {
        Map<String, String> files = new LinkedHashMap<>();
        var bean = new BeanWrapperImpl(student);
        FILE_FIELDS.keySet().forEach(key -> {
            String path = (String) bean.getPropertyValue(key + "Path");
            if (path != null) files.put(key, path);
        });
        CustomCandidateFieldJson.parseStringMap(student.getCustomFilePathsJson())
                .forEach((key, value) -> files.put("customField_" + key, value));
        return files;
    }

    static void validateDetails(CandidateCorrectionRequest request, Student student, Exam exam) {
        Set<String> allowed = permissions(exam).keySet();
        Map<String, String> required = CustomCandidateFieldJson.parseStringMap(exam.getCandidateRequiredFieldsJson());
        for (var component : CandidateCorrectionRequest.class.getRecordComponents()) {
            String name = component.getName();
            if (name.equals("registrationId") || name.equals("password")) continue;
            Object value;
            try { value = component.getAccessor().invoke(request); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            if (value == null) continue;
            String key = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
            if (!allowed.contains(key)) throw new IllegalArgumentException(name + " is not enabled for correction.");
            String text = value.toString().trim();
            boolean mandatory = Set.of("FULL_NAME", "EMAIL", "PHONE", "DATE_OF_BIRTH").contains(key)
                    || "true".equalsIgnoreCase(required.get(key));
            if (mandatory && text.isBlank()) throw new IllegalArgumentException(name + " is required.");
            int limit = switch (name) {
                case "address", "ugDetails", "pgDetails" -> 600;
                case "state", "nationality", "guardianRelation" -> 80;
                case "pincode" -> 12;
                case "gender" -> 40;
                case "phone", "parentPhone", "guardianPhone" -> 32;
                default -> 200;
            };
            if (text.length() > limit) throw new IllegalArgumentException(name + " is too long.");
            if (name.toLowerCase(Locale.ROOT).contains("email") && !text.isEmpty()
                    && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Invalid email address.");
        }
        if (request.dateOfBirth() != null && !request.dateOfBirth().isBefore(java.time.LocalDate.now()))
            throw new IllegalArgumentException("Date of birth must be in the past.");
    }

    static void validateRequired(Student student, Exam exam) {
        var bean = new BeanWrapperImpl(student);
        var required = CustomCandidateFieldJson.parseStringMap(exam.getCandidateRequiredFieldsJson());
        var allowed = permissions(exam).keySet();
        for (var component : CandidateCorrectionRequest.class.getRecordComponents()) {
            String name = component.getName();
            if (name.equals("password") || name.equals("registrationId")) continue;
            String key = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
            if (allowed.contains(key) && (Set.of("FULL_NAME", "EMAIL", "PHONE", "DATE_OF_BIRTH").contains(key)
                    || "true".equalsIgnoreCase(required.get(key)))) {
                Object value = bean.getPropertyValue(name);
                if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(name + " is required.");
            }
        }
        FILE_FIELDS.forEach((name, key) -> {
            if (allowed.contains(key) && "true".equalsIgnoreCase(required.get(key)) && bean.getPropertyValue(name + "Path") == null)
                throw new IllegalArgumentException(name + " is required.");
        });
        var values = CustomCandidateFieldJson.parseStringMap(student.getCustomFieldValuesJson());
        var paths = CustomCandidateFieldJson.parseStringMap(student.getCustomFilePathsJson());
        for (var field : CustomCandidateFieldJson.parse(exam.getCustomFieldsJson())) {
            if (!field.correctionAllowed() || !field.required()) continue;
            String value = (field.type() == CustomCandidateFieldType.TEXT || field.type() == CustomCandidateFieldType.TEXTAREA ? values : paths).get(field.key());
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field.label() + " is required.");
        }
    }

    void applyUploads(Student student, Exam exam, MultipartHttpServletRequest request) {
        Set<String> allowed = permissions(exam).keySet();
        Map<String, CustomCandidateField> custom = new LinkedHashMap<>();
        CustomCandidateFieldJson.parse(exam.getCustomFieldsJson()).forEach(field -> custom.put("customField_" + field.key(), field));
        Map<String, String> paths = files(student);
        Map<String, String> text = new LinkedHashMap<>(CustomCandidateFieldJson.parseStringMap(student.getCustomFieldValuesJson()));
        Map<String, String> customPaths = new LinkedHashMap<>(CustomCandidateFieldJson.parseStringMap(student.getCustomFilePathsJson()));
        List<String> created = new ArrayList<>();
        // Rollbacks remove only newly generated files, never previously submitted documents.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) created.forEach(storage::deleteStoredFile);
            }
        });
        request.getMultiFileMap().forEach((key, values) -> {
            if (key.equals("details")) return;
            String permission = FILE_FIELDS.getOrDefault(key, key);
            CustomCandidateField field = custom.get(key);
            if (!allowed.contains(permission) || (!FILE_FIELDS.containsKey(key) && field == null))
                throw new IllegalArgumentException("Upload is not enabled for correction: " + key);
            if (values.size() != 1) throw new IllegalArgumentException("Choose one file per field.");
            var file = values.getFirst();
            if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty: " + key);
            boolean image = key.equals("photo") || key.equals("signature") || (field != null && field.type() == CustomCandidateFieldType.IMAGE_UPLOAD);
            if (field != null && field.type() != CustomCandidateFieldType.IMAGE_UPLOAD && field.type() != CustomCandidateFieldType.PDF_UPLOAD)
                throw new IllegalArgumentException("This field does not accept a file.");
            long size = (field != null && field.maxSizeKb() != null ? field.maxSizeKb() : image ? 100 : 1024) * 1024L;
            Set<String> types = image ? Set.of("image/png", "image/jpeg")
                    : field != null ? Set.of("application/pdf") : Set.of("application/pdf", "image/png", "image/jpeg");
            if (file.getSize() > size || !types.contains(file.getContentType())) throw new IllegalArgumentException("Invalid file type or size: " + key);
            try {
                if ("application/pdf".equals(file.getContentType())) {
                    try (var pdf = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                        if (pdf.isEncrypted() || pdf.getNumberOfPages() == 0) throw new IllegalArgumentException("Upload an unlocked PDF with at least one page.");
                    }
                } else {
                    try (var stream = file.getInputStream()) {
                        if (javax.imageio.ImageIO.read(stream) == null) throw new IllegalArgumentException("The image file is invalid.");
                    }
                }
            } catch (java.io.IOException error) { throw new IllegalArgumentException("The uploaded document could not be read: " + key); }
            String path = storage.storeCustomFile(student.getRegistrationId(), key, file, size, types);
            created.add(path);
            paths.put(key, path);
            if (field != null) customPaths.put(field.key(), path);
        });
        request.getParameterMap().forEach((key, values) -> {
            if (!key.startsWith("customField_")) return;
            var field = custom.get(key);
            if (field == null || !allowed.contains(key) || (field.type() != CustomCandidateFieldType.TEXT && field.type() != CustomCandidateFieldType.TEXTAREA))
                throw new IllegalArgumentException("Field is not enabled for correction: " + key);
            String value = values[0].trim();
            if (value.length() > 2000 || (field.required() && value.isBlank())) throw new IllegalArgumentException(field.label() + " is required and must be at most 2000 characters.");
            text.put(field.key(), value);
        });
        student.attachFiles(new StoredCandidateFiles(paths.get("photo"), paths.get("signature"), paths.get("idCard"),
                paths.get("class10Marksheet"), paths.get("class12Marksheet"), paths.get("ugDocument"), paths.get("pgDocument")));
        String textJson = CustomCandidateFieldJson.stringifyStringMap(text);
        String pathsJson = CustomCandidateFieldJson.stringifyStringMap(customPaths);
        if ((textJson != null && textJson.length() > 8000) || (pathsJson != null && pathsJson.length() > 8000))
            throw new IllegalArgumentException("The combined custom-field data is too large.");
        student.attachCustomFields(textJson, pathsJson);
    }
}
