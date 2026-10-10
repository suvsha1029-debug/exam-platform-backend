package com.shashwat.examplatformbackend.notification;

import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.List;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NotificationService {
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final NotificationRepository notificationRepository;
    private final ExamRepository examRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SmtpMailSenderFactory smtpMailSenderFactory;
    private final String defaultSenderEmail;
    private final AtomicBoolean sendQueuedEmailsRunning = new AtomicBoolean(false);

    public NotificationService(
            NotificationRepository notificationRepository,
            ExamRepository examRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            SmtpMailSenderFactory smtpMailSenderFactory,
            @Value("${spring.mail.username:}") String defaultSenderEmail
    ) {
        this.notificationRepository = notificationRepository;
        this.examRepository = examRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.smtpMailSenderFactory = smtpMailSenderFactory;
        this.defaultSenderEmail = defaultSenderEmail;
    }

    public void queueRegistrationMessages(Student student, Exam exam) {
        String body = renderRegistrationBody(student, exam);
        String senderEmail = effectiveSenderEmail(exam.getNotificationSenderEmail());
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            NotificationOutbox email = notificationRepository.save(new NotificationOutbox(
                    NotificationChannel.EMAIL,
                    student.getEmail(),
                    senderEmail,
                    "Registration successful: " + student.getRegistrationId(),
                    body,
                    NotificationStatus.QUEUED
            ));
            trySendEmail(email, exam);
        }
        if (student.getPhone() != null && !student.getPhone().isBlank()) {
            notificationRepository.save(new NotificationOutbox(
                    NotificationChannel.SMS,
                    student.getPhone(),
                    senderEmail,
                    "Registration successful",
                    body,
                    NotificationStatus.QUEUED
            ));
        }
    }

    @Transactional
    public void queueCandidatePasswordReset(Student student, String resetUrl) {
        if (student.getEmail() == null || student.getEmail().isBlank()) {
            return;
        }
        NotificationOutbox email = notificationRepository.save(new NotificationOutbox(
                NotificationChannel.EMAIL,
                student.getEmail(),
                effectiveSenderEmail(null),
                "Candidate password reset request",
                "A password reset was requested for candidate registration ID " + student.getRegistrationId()
                        + ". This single-use link expires in 15 minutes:\n\n" + resetUrl
                        + "\n\nIf you did not request this change, ignore this email.",
                NotificationStatus.QUEUED
        ));
        trySendEmail(email);
    }

    @Transactional
    public void queuePlatformEmail(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        NotificationOutbox email = notificationRepository.save(new NotificationOutbox(
                NotificationChannel.EMAIL,
                recipient.trim(),
                effectiveSenderEmail(null),
                subject == null || subject.isBlank() ? "Exam service update" : subject.trim(),
                body == null || body.isBlank() ? "Your exam service request has been updated." : body.trim(),
                NotificationStatus.QUEUED
        ));
        trySendEmail(email);
    }

    @Transactional
    public SendQueuedEmailResponse sendQueuedEmails() {
        if (!sendQueuedEmailsRunning.compareAndSet(false, true)) {
            return new SendQueuedEmailResponse(0, 0, 0, 0);
        }
        int attempted = 0;
        int sentBefore;
        int failedBefore;
        int skippedBefore;
        int sentAfter;
        int failedAfter;
        int skippedAfter;

        try {
            sentBefore = countStatus(NotificationStatus.SENT);
            failedBefore = countStatus(NotificationStatus.FAILED);
            skippedBefore = countStatus(NotificationStatus.SKIPPED);

            List<NotificationOutbox> emailsToSend = notificationRepository.findAllByChannelAndStatusInOrderByCreatedAtAsc(
                    NotificationChannel.EMAIL,
                    List.copyOf(EnumSet.of(NotificationStatus.QUEUED, NotificationStatus.FAILED))
            );
            for (NotificationOutbox notification : emailsToSend) {
                attempted++;
                trySendEmail(notification);
            }

            sentAfter = countStatus(NotificationStatus.SENT);
            failedAfter = countStatus(NotificationStatus.FAILED);
            skippedAfter = countStatus(NotificationStatus.SKIPPED);

            return new SendQueuedEmailResponse(
                    attempted,
                    Math.max(0, sentAfter - sentBefore),
                    Math.max(0, failedAfter - failedBefore),
                    Math.max(0, skippedAfter - skippedBefore)
            );
        } finally {
            sendQueuedEmailsRunning.set(false);
        }
    }

    private int countStatus(NotificationStatus status) {
        return Math.toIntExact(notificationRepository.countByStatusValue(status));
    }

    private void trySendEmail(NotificationOutbox notification) {
        trySendEmail(notification, null);
    }

    private void trySendEmail(NotificationOutbox notification, Exam exam) {
        if (notification.getChannel() != NotificationChannel.EMAIL
                || (notification.getStatus() != NotificationStatus.QUEUED && notification.getStatus() != NotificationStatus.FAILED)) {
            return;
        }
        DeliverySender deliverySender = resolveDeliverySender(notification, exam);
        if (deliverySender == null) {
            notification.markFailed("SMTP sender is not configured. Add sender Gmail and app password in Exam Settings, or configure MAIL_USERNAME and MAIL_PASSWORD.");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            // Gmail only accepts a From address configured on the authenticated account.
            // Keep the authenticated mailbox as From and use the exam value only as Reply-To.
            message.setFrom(deliverySender.authenticatedSender());
            if (isValidEmail(notification.getSenderEmail())
                    && !notification.getSenderEmail().equalsIgnoreCase(deliverySender.authenticatedSender())) {
                message.setReplyTo(notification.getSenderEmail());
            }
            message.setTo(notification.getRecipient());
            message.setSubject(notification.getSubject());
            message.setText(notification.getBody());
            deliverySender.mailSender().send(message);
            notification.markSent();
        } catch (MailException exception) {
            notification.markFailed(exception.getMessage());
        }
    }

    private DeliverySender resolveDeliverySender(NotificationOutbox notification, Exam exam) {
        DeliverySender examSender = deliverySenderFromExam(exam);
        if (examSender != null) {
            return examSender;
        }
        String requestedSender = notification.getSenderEmail();
        if (isValidEmail(requestedSender)) {
            DeliverySender matchingExamSender = examRepository.findAll().stream()
                    .filter(candidateExam -> requestedSender.equalsIgnoreCase(candidateExam.getNotificationSenderEmail()))
                    .map(this::deliverySenderFromExam)
                    .filter(candidate -> candidate != null)
                    .findFirst()
                    .orElse(null);
            if (matchingExamSender != null) {
                return matchingExamSender;
            }
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        String authenticatedSender = effectiveSenderEmail(null);
        if (mailSender == null || authenticatedSender == null) {
            return null;
        }
        return new DeliverySender(mailSender, authenticatedSender);
    }

    private DeliverySender deliverySenderFromExam(Exam exam) {
        if (exam == null
                || !isValidEmail(exam.getNotificationSenderEmail())
                || !exam.hasNotificationSenderAppPassword()) {
            return null;
        }
        String senderEmail = exam.getNotificationSenderEmail().trim();
        return new DeliverySender(
                smtpMailSenderFactory.create(senderEmail, exam.getNotificationSenderAppPassword()),
                senderEmail
        );
    }

    private String effectiveSenderEmail(String examSenderEmail) {
        if (examSenderEmail != null && !examSenderEmail.isBlank()) {
            return examSenderEmail.trim();
        }
        if (defaultSenderEmail != null && !defaultSenderEmail.isBlank()) {
            return defaultSenderEmail.trim();
        }
        return null;
    }

    private static boolean isValidEmail(String value) {
        return value != null && SIMPLE_EMAIL.matcher(value.trim()).matches();
    }

    private record DeliverySender(JavaMailSender mailSender, String authenticatedSender) {
    }

    private static String renderRegistrationBody(Student student, Exam exam) {
        String template = exam.getRegistrationEmailBody();
        if (template == null || template.isBlank()) {
            template = "Your registration was successful for {{examName}}. Registration ID: {{registrationId}}. Use the password you created during registration to log in.";
        }
        return template
                .replace("{{registrationId}}", student.getRegistrationId())
                .replace("{{name}}", student.getFullName())
                .replace("{{examCode}}", student.getExamCode())
                .replace("{{examName}}", exam.getName())
                .replace("{{streamCode}}", student.getStreamCode() == null ? "-" : student.getStreamCode());
    }
}
