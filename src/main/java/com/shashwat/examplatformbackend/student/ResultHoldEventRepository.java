package com.shashwat.examplatformbackend.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResultHoldEventRepository extends JpaRepository<ResultHoldEvent, Long> {
    Page<ResultHoldEvent> findByRegistrationIdOrderByDecidedAtDescIdDesc(String registrationId, Pageable pageable);
}
