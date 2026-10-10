package com.shashwat.examplatformbackend.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstituteServiceRequestRepository extends JpaRepository<InstituteServiceRequest, Long> {
    List<InstituteServiceRequest> findAllByOrderByCreatedAtDesc();

    List<InstituteServiceRequest> findAllByStatusOrderByCreatedAtDesc(InstituteServiceRequestStatus status);
}
