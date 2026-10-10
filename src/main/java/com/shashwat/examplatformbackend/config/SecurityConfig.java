package com.shashwat.examplatformbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PublicApiBoundaryFilter publicApiBoundaryFilter,
            DesktopConsoleGateFilter desktopConsoleGateFilter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(publicApiBoundaryFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(desktopConsoleGateFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; object-src 'none'; frame-src 'self'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/uploads/**", "/question-papers/**", "/answer-keys/**", "/original-documents/**", "/portal-branding/**").denyAll()
                        .requestMatchers("/", "/index.html", "/service-request.html", "/admin.html", "/admin-7xk92q-portal.html", "/master-admin.html", "/platform-control.html", SecureConsolePaths.MASTER_ADMIN_SETUP, "/assets/**", "/template-vault/**", "/candidate-entry/**", "/candidate-secure/**", "/candidate-secure-exam-window", "/candidate-error.html", "/candidate-registration.html", "/candidate-registration-personal.html", "/candidate-registration-education.html", "/candidate-registration-uploads.html", "/candidate-registration-fee.html", "/candidate-registration-preview.html", "/candidate-dashboard.html", "/candidate-details.html", "/candidate-correction.html", "/candidate-exam-lobby.html", "/candidate-exam.html", "/candidate-responses.html", "/api/auth/login", "/api/admin-access/setup-password", "/api/desktop-console/enroll", "/api/candidate/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/candidate-security-token", "/api/public/exams", "/api/public/exams/open", "/api/public/exams/portal-banner/**", "/api/public/exams/*/notice-document.pdf", "/api/public/candidate-password-reset/captcha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/institute-service-requests", "/api/public/candidate-password-reset/request", "/api/public/candidate-password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/students/register", "/api/students/register-with-files").permitAll()
                        .requestMatchers(SecureConsolePaths.PLATFORM, "/api/platform/**", SecureConsolePaths.MASTER_ADMIN, "/api/master-admin/**").hasRole("MASTER_ADMIN")
                        .requestMatchers(SecureConsolePaths.ADMIN, "/api/admin/**", "/api/exams/**", "/api/students/**", "/api/notifications/**", "/api/responses/**", "/api/evaluation/**", "/api/exam-access/**", "/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getOutputStream().write("{\"title\":\"Authentication required\",\"detail\":\"Log in as admin before using this API.\"}".getBytes(StandardCharsets.UTF_8));
                            response.getOutputStream().flush();
                        }, request -> request.getRequestURI().startsWith("/api/"))
                )
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                        .maximumSessions(1)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .permitAll()
                        .successHandler((request, response, authentication) -> {
                            boolean master = authentication.getAuthorities().stream()
                                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_MASTER_ADMIN"));
                            response.sendRedirect(master ? SecureConsolePaths.MASTER_ADMIN : SecureConsolePaths.ADMIN);
                        }))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new SecurePasswordEncoder();
    }
}
