package com.shashwat.examplatformbackend.notification;

import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class MailHealthService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ExamRepository examRepository;
    private final SmtpMailSenderFactory smtpMailSenderFactory;
    private final String sender;

    public MailHealthService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ExamRepository examRepository,
            SmtpMailSenderFactory smtpMailSenderFactory,
            @Value("${spring.mail.username:}") String sender
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.examRepository = examRepository;
        this.smtpMailSenderFactory = smtpMailSenderFactory;
        this.sender = sender == null ? "" : sender.trim();
    }

    public MailHealthResponse check(String examCode) {
        if (examCode != null && !examCode.isBlank()) {
            Exam exam = examRepository.findByExamCode(examCode.trim()).orElse(null);
            if (exam != null && exam.getNotificationSenderEmail() != null && !exam.getNotificationSenderEmail().isBlank()) {
                if (!exam.hasNotificationSenderAppPassword()) {
                    return new MailHealthResponse(false, false, exam.getNotificationSenderEmail(), "This exam has a sender email but no saved Google app password.");
                }
                JavaMailSenderImpl examSender = smtpMailSenderFactory.create(
                        exam.getNotificationSenderEmail(),
                        exam.getNotificationSenderAppPassword()
                );
                return testSender(examSender, exam.getNotificationSenderEmail(), "Exam Gmail SMTP authentication is working.");
            }
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!(mailSender instanceof JavaMailSenderImpl smtpSender) || sender.isBlank() || smtpSender.getPassword() == null || smtpSender.getPassword().isBlank()) {
            return new MailHealthResponse(false, false, sender, "SMTP credentials are not configured.");
        }
        return testSender(smtpSender, sender, "Gmail SMTP authentication is working.");
    }

    private MailHealthResponse testSender(JavaMailSenderImpl smtpSender, String senderEmail, String successMessage) {
        try {
            smtpSender.testConnection();
            return new MailHealthResponse(true, true, senderEmail, successMessage);
        } catch (Exception exception) {
            return new MailHealthResponse(true, false, senderEmail,
                    "Gmail rejected the configured credentials. Create a new Google app password for this sender and save it again.");
        }
    }
}
