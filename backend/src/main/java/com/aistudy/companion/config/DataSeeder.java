package com.aistudy.companion.config;

import com.aistudy.companion.entity.User;
import com.aistudy.companion.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures a single admin account exists on startup so the Admin Dashboard is
 * reachable without manual DB surgery. Configure via ADMIN_EMAIL /
 * ADMIN_PASSWORD env vars in real deployments; sensible dev defaults are
 * provided but should be changed for anything beyond local evaluation.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@studycompanion.local}")
    private String adminEmail;

    @Value("${app.admin.password:ChangeMe123!}")
    private String adminPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail.toLowerCase())) return;
        User admin = User.builder()
                .email(adminEmail.toLowerCase())
                .displayName("Admin")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Seeded default admin account: {}", adminEmail);
    }
}
