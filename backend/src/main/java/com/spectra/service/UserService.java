package com.spectra.service;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.Enterprise;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnterpriseRepository enterpriseRepository;

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) EnterpriseRepository enterpriseRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enterpriseRepository = enterpriseRepository;
    }

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, null);
    }

    @Transactional
    public User registerUser(String fullName, String email, String rawPassword) {
        return registerUser(fullName, email, rawPassword, null);
    }

    @Transactional
    public User registerUser(String fullName, String email, String rawPassword, String organizationCode) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);

        if (enterpriseRepository != null) {
            if (organizationCode != null && !organizationCode.trim().isEmpty()) {
                Enterprise targetEnterprise = enterpriseRepository.findByOrganizationCodeIgnoreCase(organizationCode.trim())
                        .orElseThrow(() -> new BadRequestException("Unknown enterprise organization code: " + organizationCode.trim()));
                user.setEnterprise(targetEnterprise);
            } else {
                User saved = userRepository.save(user);
                Enterprise personalEnt = new Enterprise();
                String display = saved.getFullName() != null && !saved.getFullName().isBlank()
                        ? saved.getFullName().trim() + " Enterprise"
                        : saved.getEmail();
                personalEnt.setName(display.length() > 150 ? display.substring(0, 150) : display);
                personalEnt.setSlug("ent-user-" + saved.getId());
                personalEnt.setOrganizationCode(("ORG-" + saved.getId()).toUpperCase(Locale.ROOT));
                personalEnt.setEnvironment("PRODUCTION");
                personalEnt.setConnectionStatus(Enterprise.STATUS_CONNECTED);
                personalEnt.setContactName(saved.getFullName());
                personalEnt.setContactEmail(saved.getEmail());
                Enterprise savedEnt = enterpriseRepository.save(personalEnt);
                saved.setEnterprise(savedEnt);
                return userRepository.save(saved);
            }
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmailWithEnterprise(normalizedEmail)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User updateUserRole(Long id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setRole(role);
        return userRepository.save(user);
    }
}
