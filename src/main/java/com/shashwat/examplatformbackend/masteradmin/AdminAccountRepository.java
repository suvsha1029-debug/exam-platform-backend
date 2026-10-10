package com.shashwat.examplatformbackend.masteradmin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmailIgnoreCase(String email);

    List<AdminAccount> findAllByOrderByCreatedAtDesc();
}
