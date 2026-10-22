package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.ServiceMetric;

public interface ServiceMetricRepository extends JpaRepository<ServiceMetric, Long> {

    List<ServiceMetric> findByServiceIdOrderByRecordedAtDescIdDesc(Long serviceId, Pageable pageable);

    List<ServiceMetric> findByServiceIdAndServiceUserIdOrderByRecordedAtDescIdDesc(Long serviceId, Long userId, Pageable pageable);

    List<ServiceMetric> findAllByOrderByRecordedAtDescIdDesc(Pageable pageable);

    List<ServiceMetric> findByServiceUserIdOrderByRecordedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<ServiceMetric> findByIdAndServiceUserId(Long id, Long userId);
}
