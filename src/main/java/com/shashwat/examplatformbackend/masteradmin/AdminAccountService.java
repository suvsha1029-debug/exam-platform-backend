package com.shashwat.examplatformbackend.masteradmin;

import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.AdminAccountResponse;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.InviteAdminRequest;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.PasswordSetupRequest;
import com.shashwat.examplatformbackend.masteradmin.AdminAccountDtos.PasswordSetupResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminAccountService {
    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapMasterEmail;

    public AdminAccountService(
            AdminAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${app.master-admin.email:}") String bootstrapMasterEmail
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapMasterEmail = normalizeEmail(bootstrapMasterEmail);
    }

    @PostConstruct
    @Transactional
    void ensureBootstrapMasterAccount() {
        if (bootstrapMasterEmail.isBlank()) {
            return;
        }
        repository.findByEmailIgnoreCase(bootstrapMasterEmail).orElseGet(() ->
                repository.save(new AdminAccount(bootstrapMasterEmail, "Master Admin", AdminAccountRole.MASTER_ADMIN))
        );
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAccounts() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional
    public AdminAccountResponse invite(InviteAdminRequest request) {
        String email = normalizeEmail(request.email());
        AdminAccount account = repository.findByEmailIgnoreCase(email)
                .orElseGet(() -> new AdminAccount(email, clean(request.adminName()), AdminAccountRole.ADMIN));
        if (account.getRole() == AdminAccountRole.MASTER_ADMIN) {
            throw new IllegalArgumentException("The master-admin account cannot be replaced by an admin invite.");
        }
        account.updateProfile(
                clean(request.adminName()),
                normalizePhone(request.phoneNumber()),
                request.dateOfBirth(),
                clean(request.examName()),
                clean(request.serviceAvailed())
        );
        account.setActive(true);
        return response(repository.save(account));
    }

    @Transactional
    public AdminAccountResponse setAccess(Long id, boolean active) {
        AdminAccount account = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found."));
        if (account.getRole() == AdminAccountRole.MASTER_ADMIN && !active) {
            throw new IllegalArgumentException("The bootstrap master-admin account cannot be revoked.");
        }
        account.setActive(active);
        return response(repository.save(account));
    }

    @Transactional
    public AdminAccountResponse requirePasswordReset(Long id) {
        AdminAccount account = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin account was not found."));
        account.requirePasswordSetup();
        return response(repository.save(account));
    }

    @Transactional
    public PasswordSetupResponse setupPassword(PasswordSetupRequest request) {
        String email = normalizeEmail(request.email());
        AdminAccount account = repository.findByEmailIgnoreCase(email)
                .orElseThrow(AdminAccountService::invalidSetupRequest);
        if (!account.isPasswordSetupRequired()) {
            throw invalidSetupRequest();
        }

        String phone = normalizePhone(request.phoneNumber());
        boolean firstMasterSetup = account.getRole() == AdminAccountRole.MASTER_ADMIN
                && account.isPasswordSetupRequired()
                && account.getPhoneNumber() == null
                && account.getDateOfBirth() == null
                && email.equals(bootstrapMasterEmail);

        if (!firstMasterSetup && (!phone.equals(normalizePhone(account.getPhoneNumber()))
                || !request.dateOfBirth().equals(account.getDateOfBirth()))) {
            throw invalidSetupRequest();
        }
        validatePasswordStrength(request.password());
        account.updateProfile(
                clean(request.adminName()),
                phone,
                request.dateOfBirth(),
                account.getExamName(),
                account.getServiceAvailed()
        );
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setActive(true);
        repository.save(account);
        return new PasswordSetupResponse(account.getEmail(), account.getRole().name(), true, "Password saved. You can now sign in.");
    }

    private AdminAccountResponse response(AdminAccount account) {
        return new AdminAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getAdminName(),
                account.getPhoneNumber(),
                account.getDateOfBirth(),
                account.getExamName(),
                account.getServiceAvailed(),
                account.getRole().name(),
                account.isActive(),
                account.isPasswordSetupRequired(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    static String normalizeEmail(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String value) {
        return clean(value).replaceAll("[^0-9+]", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static IllegalArgumentException invalidSetupRequest() {
        return new IllegalArgumentException("Unable to verify the admin setup request.");
    }

    private static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 12
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Use at least 12 characters with uppercase, lowercase, number, and symbol.");
        }
    }
}
