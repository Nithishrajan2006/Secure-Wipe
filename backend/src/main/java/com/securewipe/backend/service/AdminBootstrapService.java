package com.securewipe.backend.service;

import com.securewipe.backend.entity.User;
import com.securewipe.backend.enums.UserRole;
import com.securewipe.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService implements ApplicationRunner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_ADMIN_USERNAME = "securewipe-admin";
    private static final String DEFAULT_ADMIN_FULL_NAME = "System Administrator";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:}")
    private String configuredUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> adminUsers = userRepository.findByRole(UserRole.ADMIN);

        if (adminUsers.isEmpty()) {
            createBootstrapAdmin();
            return;
        }

        User existingAdmin = adminUsers.get(0);
        if (isLegacyHardcodedAdmin(existingAdmin)) {
            rotateLegacyAdmin(existingAdmin);
            return;
        }

        log.info("[BOOTSTRAP] Existing admin account detected. Skipping bootstrap seeding.");
    }

    private void createBootstrapAdmin() {
        String username = resolveUsername();
        String password = resolvePassword();
        String email = resolveEmail(username);

        User admin = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(DEFAULT_ADMIN_FULL_NAME)
                .role(UserRole.ADMIN)
                .isActive(true)
                .build();

        userRepository.save(admin);
        log.warn("[BOOTSTRAP] SecureWipe admin bootstrap created. Username: {} Password: {}",
                username, password);
    }

    private void rotateLegacyAdmin(User admin) {
        String username = resolveUsername();
        String password = resolvePassword();
        String email = resolveEmail(username);

        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setFullName(DEFAULT_ADMIN_FULL_NAME);

        userRepository.save(admin);
        log.warn("[BOOTSTRAP] Legacy hardcoded admin credentials detected and rotated. Username: {} Password: {}",
                username, password);
    }

    private boolean isLegacyHardcodedAdmin(User admin) {
        return Objects.equals(admin.getUsername(), "admin")
                && Objects.equals(admin.getEmail(), "admin@securewipe.gov.in")
                && passwordEncoder.matches("Admin@123", admin.getPassword());
    }

    private String resolveUsername() {
        if (configuredUsername != null && !configuredUsername.isBlank()) {
            return configuredUsername.trim();
        }
        return DEFAULT_ADMIN_USERNAME + "-" + generateTokenSegment();
    }

    private String resolvePassword() {
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            return configuredPassword;
        }
        return generateSecret();
    }

    private String resolveEmail(String username) {
        return username + "@securewipe.local";
    }

    private String generateSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateTokenSegment() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
