package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.Enterprise;

public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {

    Optional<Enterprise> findBySlugIgnoreCase(String slug);

    Optional<Enterprise> findByOrganizationCodeIgnoreCase(String organizationCode);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByOrganizationCodeIgnoreCase(String organizationCode);

    List<Enterprise> findAllByOrderByCreatedAtDesc();

    List<Enterprise> findByConnectionStatusOrderByCreatedAtDesc(String connectionStatus);

    long countByConnectionStatus(String connectionStatus);
}
