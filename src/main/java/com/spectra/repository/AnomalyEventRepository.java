package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.AnomalyEvent;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, Long> {

    List<AnomalyEvent> findAllByOrderByDetectedAtDescIdDesc(Pageable pageable);

    List<AnomalyEvent> findByServiceUserIdOrderByDetectedAtDescIdDesc(Long userId, Pageable pageable);

    List<AnomalyEvent> findByServiceIdOrderByDetectedAtDescIdDesc(Long serviceId, Pageable pageable);

    List<AnomalyEvent> findByServiceIdAndServiceUserIdOrderByDetectedAtDescIdDesc(Long serviceId, Long userId, Pageable pageable);

    Optional<AnomalyEvent> findByIdAndServiceUserId(Long id, Long userId);

    boolean existsByMetricId(Long metricId);
}
