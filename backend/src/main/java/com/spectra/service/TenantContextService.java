package com.spectra.service;

import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.Enterprise;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.UserRepository;

/**
 * Shared multi-enterprise tenant resolution and strict Admin read-only guard.
 */
@Service
public class TenantContextService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;

    public TenantContextService(UserRepository userRepository, EnterpriseRepository enterpriseRepository) {
        this.userRepository = userRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    public User getCurrentUser() {
        if (userRepository == null) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmailWithEnterprise(auth.getName()).orElse(null);
    }

    public boolean isPrincipalAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        User user = getCurrentUser();
        return user != null && user.getRole() == Role.ADMIN;
    }

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    /**
     * Enforces strict read-only access for Platform Administrators on operational mutations.
     */
    public void assertOperationalWriteAllowed() {
        if (isPrincipalAdmin()) {
            throw new AccessDeniedException("Platform administrators have read-only access to enterprise operational records");
        }
    }

    /**
     * Ensures every non-admin user is bound to an Enterprise (auto-creating a personal
     * tenant workspace if registered standalone without an explicit enterprise assignment).
     */
    @Transactional
    public Enterprise resolveOrProvisionEnterprise(User user) {
        if (user == null || user.getRole() == Role.ADMIN) {
            return null;
        }
        if (user.getEnterprise() != null) {
            return user.getEnterprise();
        }
        if (enterpriseRepository == null) {
            return null;
        }
        String slug = "ent-user-" + user.getId();
        Enterprise enterprise = enterpriseRepository.findBySlugIgnoreCase(slug).orElseGet(() -> {
            Enterprise ent = new Enterprise();
            String display = user.getFullName() != null && !user.getFullName().isBlank()
                    ? user.getFullName().trim() + " Enterprise"
                    : user.getEmail();
            ent.setName(display.length() > 150 ? display.substring(0, 150) : display);
            ent.setSlug(slug);
            ent.setOrganizationCode(("ORG-" + user.getId()).toUpperCase(Locale.ROOT));
            ent.setEnvironment("PRODUCTION");
            ent.setConnectionStatus(Enterprise.STATUS_CONNECTED);
            ent.setContactName(user.getFullName());
            ent.setContactEmail(user.getEmail());
            return enterpriseRepository.save(ent);
        });
        user.setEnterprise(enterprise);
        userRepository.save(user);
        return enterprise;
    }

    public Long resolveEnterpriseId(User user) {
        Enterprise ent = resolveOrProvisionEnterprise(user);
        return ent != null ? ent.getId() : null;
    }

    /**
     * Checks whether a non-admin user belongs to the same Enterprise (or legacy user workspace)
     * as the target MonitoredService.
     */
    public boolean canAccessService(User currentUser, MonitoredService service) {
        if (service == null) {
            return false;
        }
        if (currentUser == null) {
            return true;
        }
        if (isAdmin(currentUser)) {
            return true;
        }
        if (currentUser.getEnterprise() != null && service.getEnterprise() != null) {
            return currentUser.getEnterprise().getId().equals(service.getEnterprise().getId());
        }
        return service.getUser() != null && service.getUser().getId() == currentUser.getId();
    }
}
