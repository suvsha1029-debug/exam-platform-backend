package com.shashwat.examplatformbackend.config;

import com.shashwat.examplatformbackend.notification.SmtpMailSenderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailSenderConfig {
    @Bean
    JavaMailSender javaMailSender(
            @Value("${spring.mail.host:smtp.gmail.com}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            SmtpMailSenderFactory smtpMailSenderFactory
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(SmtpMailSenderFactory.clean(username));
        sender.setPassword(SmtpMailSenderFactory.clean(password));

        smtpMailSenderFactory.applyGmailTlsProperties(sender.getJavaMailProperties());
        return sender;
    }
}
