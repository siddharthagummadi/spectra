package com.spectra.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.Enterprise;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;

/**
 * Idempotent multi-enterprise schema index and backfill initializer.
 * Ensures existing users and services seamlessly bind to an Enterprise on startup.
 */
@Configuration
public class EnterpriseMigrationInitializer {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseMigrationInitializer.class);

    @Bean
    @Profile("!test")
    public ApplicationRunner enterpriseSchemaInitializer(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            EnterpriseRepository enterpriseRepository,
            MonitoredServiceRepository serviceRepository) {
        return args -> {
            ensureIndexes(jdbcTemplate);
            backfillEnterprises(userRepository, enterpriseRepository, serviceRepository);
        };
    }

    private void ensureIndexes(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_service_metrics_agent_dedup "
                    + "ON service_metrics (service_id, metric_name, unit, recorded_at) "
                    + "WHERE agent_id IS NOT NULL"
            );
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_monitored_services_enterprise_name "
                    + "ON monitored_services (enterprise_id, LOWER(name)) "
                    + "WHERE enterprise_id IS NOT NULL"
            );
        } catch (Exception ex) {
            log.warn("Non-fatal enterprise index creation warning: {}", ex.getMessage());
        }
    }

    @Transactional
    protected void backfillEnterprises(
            UserRepository userRepository,
            EnterpriseRepository enterpriseRepository,
            MonitoredServiceRepository serviceRepository) {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (u.getRole() != Role.ADMIN && u.getEnterprise() == null) {
                Enterprise ent = resolveOrCreatePersonalEnterprise(u, enterpriseRepository);
                u.setEnterprise(ent);
                userRepository.save(u);
            }
        }

        List<MonitoredService> services = serviceRepository.findAll();
        for (MonitoredService svc : services) {
            if (svc.getEnterprise() == null && svc.getUser() != null) {
                User owner = svc.getUser();
                Enterprise ent = owner.getEnterprise();
                if (ent == null && owner.getRole() != Role.ADMIN) {
                    ent = resolveOrCreatePersonalEnterprise(owner, enterpriseRepository);
                    owner.setEnterprise(ent);
                    userRepository.save(owner);
                }
                if (ent != null) {
                    svc.setEnterprise(ent);
                    serviceRepository.save(svc);
                }
            }
        }
    }

    private Enterprise resolveOrCreatePersonalEnterprise(User user, EnterpriseRepository enterpriseRepository) {
        String slug = "ent-user-" + user.getId();
        return enterpriseRepository.findBySlugIgnoreCase(slug).orElseGet(() -> {
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
    }
}
