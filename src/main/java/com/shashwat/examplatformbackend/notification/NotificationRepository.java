package com.shashwat.examplatformbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findAllByOrderByCreatedAtDesc();

    Page<NotificationOutbox> findAllBy(Pageable pageable);

    List<NotificationOutbox> findAllByChannelAndStatusOrderByCreatedAtAsc(NotificationChannel channel, NotificationStatus status);

    List<NotificationOutbox> findAllByChannelAndStatusInOrderByCreatedAtAsc(NotificationChannel channel, List<NotificationStatus> statuses);

    @Query("select count(n) from NotificationOutbox n where n.status = :status")
    long countByStatusValue(@Param("status") NotificationStatus status);
}
