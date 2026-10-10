package com.shashwat.examplatformbackend.masteradmin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAccountUserDetailsService implements UserDetailsService {
    private final AdminAccountRepository repository;
    private final String legacyUsername;
    private final String legacyPasswordHash;
    private final boolean legacyEnabled;

    public AdminAccountUserDetailsService(
            AdminAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String legacyUsername,
            @Value("${app.admin.password}") String legacyPassword
    ) {
        this.repository = repository;
        this.legacyUsername = legacyUsername;
        this.legacyPasswordHash = passwordEncoder.encode(legacyPassword);
        this.legacyEnabled = !legacyUsername.isBlank() && !legacyPassword.isBlank();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByEmailIgnoreCase(username)
                .map(this::databaseUser)
                .orElseGet(() -> legacyUser(username));
    }

    private UserDetails databaseUser(AdminAccount account) {
        String password = account.getPasswordHash() == null ? "{noop}PASSWORD_SETUP_REQUIRED" : account.getPasswordHash();
        User.UserBuilder builder = User.withUsername(account.getEmail())
                .password(password)
                .disabled(!account.isActive() || account.isPasswordSetupRequired());
        if (account.getRole() == AdminAccountRole.MASTER_ADMIN) {
            builder.roles("ADMIN", "MASTER_ADMIN");
        } else {
            builder.roles("ADMIN");
        }
        return builder.build();
    }

    private UserDetails legacyUser(String username) {
        if (!legacyEnabled || !legacyUsername.equals(username)) {
            throw new UsernameNotFoundException("Admin account was not found.");
        }
        return User.withUsername(legacyUsername)
                .password(legacyPasswordHash)
                .roles("ADMIN")
                .build();
    }
}
