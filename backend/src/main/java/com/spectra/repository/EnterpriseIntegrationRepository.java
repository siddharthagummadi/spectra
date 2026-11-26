package com.spectra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spectra.entity.EnterpriseIntegration;

@Repository
public interface EnterpriseIntegrationRepository extends JpaRepository<EnterpriseIntegration, Long> {

    List<EnterpriseIntegration> findAllByOrderByCreatedAtDesc();

    List<EnterpriseIntegration> findByRequestedByIdOrderByCreatedAtDesc(Long requestedById);

    List<EnterpriseIntegration> findByStatusOrderByCreatedAtDesc(String status);

    List<EnterpriseIntegration> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);
}
