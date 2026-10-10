package com.shashwat.examplatformbackend.student;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;

@Service
public class ResultControlService {
    private final StudentRepository students;
    private final ResultHoldEventRepository events;
    public ResultControlService(StudentRepository students, ResultHoldEventRepository events) {
        this.students = students;
        this.events = events;
    }
    public record Candidate(String registrationId, String name, String examCode, boolean withheld) {
        static Candidate from(Student s) { return new Candidate(s.getRegistrationId(), s.getFullName(), s.getExamCode(), s.isResultWithheld()); }
    }
    public record Candidates(List<Candidate> content, int page, int totalPages, long totalElements) {}
    @Transactional(readOnly = true)
    public Candidates list(String exam, String search, boolean heldOnly, int page) {
        var rows = students.searchResultControls(exam.trim(), search.trim(), heldOnly,
                PageRequest.of(Math.max(0, page), 20, Sort.by("registrationId")));
        return new Candidates(rows.map(Candidate::from).getContent(), rows.getNumber(), rows.getTotalPages(), rows.getTotalElements());
    }
    @Transactional
    public Candidate decide(String registrationId, boolean withheld, boolean expectedWithheld, String reason, String actor) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("Enter a reason between 5 and 1000 characters.");
        }
        Student student = students.findForResultDecision(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found."));
        if (student.isResultWithheld() != expectedWithheld) {
            throw new IllegalStateException("Result status changed. Refresh before making another decision.");
        }
        if (withheld != student.isResultWithheld()) {
            student.setResultWithheld(withheld);
            students.save(student);
            events.save(new ResultHoldEvent(registrationId, withheld, reason.trim(), actor));
        }
        return Candidate.from(student);
    }
    @Transactional(readOnly = true)
    public List<ResultHoldEvent> history(String registrationId, int page) {
        return events.findByRegistrationIdOrderByDecidedAtDescIdDesc(registrationId, PageRequest.of(Math.max(0, page), 20)).getContent();
    }
}
