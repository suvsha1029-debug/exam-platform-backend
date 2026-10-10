package com.shashwat.examplatformbackend.notification;

import com.shashwat.examplatformbackend.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final MailHealthService mailHealthService;

    public NotificationController(
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            MailHealthService mailHealthService
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.mailHealthService = mailHealthService;
    }

    @GetMapping
    public List<NotificationResponse> list() {
        return notificationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<NotificationResponse> listPage(@RequestParam int page, @RequestParam int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(
                notificationRepository.findAllBy(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(NotificationResponse::from)
        );
    }

    @PostMapping("/send-queued-email")
    public SendQueuedEmailResponse sendQueuedEmail() {
        return notificationService.sendQueuedEmails();
    }

    @GetMapping("/mail-health")
    public MailHealthResponse mailHealth(@RequestParam(required = false) String examCode) {
        return mailHealthService.check(examCode);
    }

    @DeleteMapping
    public void deleteAll() {
        notificationRepository.deleteAll();
    }
}
