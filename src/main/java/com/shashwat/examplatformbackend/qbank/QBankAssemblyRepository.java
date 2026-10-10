package com.shashwat.examplatformbackend.qbank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QBankAssemblyRepository extends JpaRepository<QBankAssemblyRecord, Long> {
    Optional<QBankAssemblyRecord> findByExamCodeAndAssemblyId(String examCode, String assemblyId);

    List<QBankAssemblyRecord> findAllByExamCodeOrderByCreatedAtDesc(String examCode);
}
