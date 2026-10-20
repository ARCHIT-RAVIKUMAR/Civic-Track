package com.civictrack.config;

import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs once at startup.
 *  1. If ADMIN_EMAIL + ADMIN_PASSWORD are set, creates that administrator (if missing).
 *  2. If civictrack.seed-demo-data=true (dev profile only), creates demo users for each role.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedDemo;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${civictrack.admin.email:}") String adminEmail,
                           @Value("${civictrack.admin.password:}") String adminPassword,
                           @Value("${civictrack.seed-demo-data:false}") boolean seedDemo) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.seedDemo = seedDemo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!adminEmail.isBlank() && adminPassword.length() >= 8) {
            createIfMissing("Administrator", adminEmail, adminPassword, Role.ADMIN);
        }
        if (seedDemo) {
            log.warn("DEMO DATA ENABLED - demo accounts use well-known passwords. Never use this in production.");
            createIfMissing("Demo Admin", "admin@civictrack.local", "Admin@1234", Role.ADMIN);
            createIfMissing("Demo Authority", "authority@civictrack.local", "Demo@1234", Role.AUTHORITY);
            createIfMissing("Demo Worker", "worker@civictrack.local", "Demo@1234", Role.FIELD_WORKER);
            createIfMissing("Demo Citizen", "citizen@civictrack.local", "Demo@1234", Role.CITIZEN);
        }
    }

    private void createIfMissing(String name, String email, String rawPassword, Role role) {
        String normalized = email.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalized)) {
            userRepository.save(new User(name, normalized, passwordEncoder.encode(rawPassword), role));
            log.info("Created {} account: {}", role, normalized);
        }
    }
}
