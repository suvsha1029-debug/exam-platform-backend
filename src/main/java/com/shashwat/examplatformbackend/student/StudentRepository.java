package com.shashwat.examplatformbackend.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.registrationId = :registrationId")
    Optional<Student> findForResultDecision(@Param("registrationId") String registrationId);

    @Query("select s from Student s where (:exam = '' or s.examCode = :exam) and (:search = '' or lower(s.registrationId) like lower(concat('%', :search, '%')) or lower(s.fullName) like lower(concat('%', :search, '%'))) and (:heldOnly = false or s.resultWithheld = true)")
    Page<Student> searchResultControls(@Param("exam") String exam, @Param("search") String search,
            @Param("heldOnly") boolean heldOnly, Pageable pageable);
    boolean existsByRegistrationId(String registrationId);

    boolean existsByExamCodeAndEmailIgnoreCase(String examCode, String email);

    Optional<Student> findByRegistrationId(String registrationId);

    List<Student> findAllByOrderByCreatedAtDesc();

    Page<Student> findAllBy(Pageable pageable);

    Page<Student> findAllByExamCode(String examCode, Pageable pageable);
}
