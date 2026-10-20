package com.civictrack.service.impl;

import com.civictrack.dto.AdminUserForm;
import com.civictrack.dto.RegistrationForm;
import com.civictrack.entity.User;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.UserRepository;
import com.civictrack.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** COMPOSITION: built from a repository and a password encoder it is given (constructor injection). */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(RegistrationForm form) {
        return create(form.getFullName(), form.getEmail(), form.getPassword(), Role.CITIZEN);
    }

    @Override
    public User createByAdmin(AdminUserForm form, User admin) {
        requireAdmin(admin);
        return create(form.getFullName(), form.getEmail(), form.getPassword(), form.getRole());
    }

    private User create(String fullName, String email, String rawPassword, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        String hash = passwordEncoder.encode(rawPassword); // never store the raw password
        return userRepository.save(new User(fullName, normalizedEmail, hash, role));
    }

    @Override
    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveFieldWorkers() {
        return userRepository.findByRoleOrderByFullNameAsc(Role.FIELD_WORKER).stream()
                .filter(User::isEnabled)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Override
    public void changeRole(UUID userId, Role newRole, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("You cannot change your own role.");
        }
        target.changeRole(newRole);
        userRepository.save(target);
    }

    @Override
    public void toggleEnabled(UUID userId, User admin) {
        requireAdmin(admin);
        User target = getById(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BusinessRuleException("You cannot disable your own account.");
        }
        if (target.isEnabled()) {
            target.disable();
        } else {
            target.enable();
        }
        userRepository.save(target);
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.hasRole(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only administrators can do this.");
        }
    }
}
