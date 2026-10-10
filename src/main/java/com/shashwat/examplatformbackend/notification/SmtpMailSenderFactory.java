package com.shashwat.examplatformbackend.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class SmtpMailSenderFactory {
    private final String host;
    private final int port;

    public SmtpMailSenderFactory(
            @Value("${spring.mail.host:smtp.gmail.com}") String host,
            @Value("${spring.mail.port:587}") int port
    ) {
        this.host = host == null || host.isBlank() ? "smtp.gmail.com" : host.trim();
        this.port = port;
    }

    public JavaMailSenderImpl create(String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(clean(username));
        sender.setPassword(clean(password));
        applyGmailTlsProperties(sender.getJavaMailProperties());
        return sender;
    }

    public void applyGmailTlsProperties(Properties properties) {
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        properties.put("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
    }

    public static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }
}
