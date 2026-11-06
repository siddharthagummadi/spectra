package com.spectra.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.ServiceMetric;

public interface ServiceMetricRepository extends JpaRepository<ServiceMetric, Long> {

    List<ServiceMetric> findByServiceIdOrderByRecordedAtDescIdDesc(Long serviceId, Pageable pageable);

    List<ServiceMetric> findByServiceIdAndServiceUserIdOrderByRecordedAtDescIdDesc(Long serviceId, Long userId, Pageable pageable);

    List<ServiceMetric> findAllByOrderByRecordedAtDescIdDesc(Pageable pageable);

    List<ServiceMetric> findByServiceUserIdOrderByRecordedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<ServiceMetric> findByIdAndServiceUserId(Long id, Long userId);

    @Query("SELECT m FROM ServiceMetric m WHERE m.service.id = :serviceId AND m.metricName = :metricName AND m.unit = :unit AND " +
           "m.id <> :metricId AND " +
           "(m.recordedAt < :recordedAt OR (m.recordedAt = :recordedAt AND m.id < :metricId)) " +
           "ORDER BY m.recordedAt DESC, m.id DESC")
    List<ServiceMetric> findHistoricalBaselineBeforeId(
            @Param("serviceId") Long serviceId,
            @Param("metricName") String metricName,
            @Param("unit") String unit,
            @Param("recordedAt") Instant recordedAt,
            @Param("metricId") Long metricId,
            Pageable pageable
    );

    @Query("SELECT m FROM ServiceMetric m WHERE m.service.id = :serviceId AND m.metricName = :metricName AND m.unit = :unit AND " +
           "m.recordedAt < :recordedAt " +
           "ORDER BY m.recordedAt DESC, m.id DESC")
    List<ServiceMetric> findHistoricalBaselineBeforeTimestamp(
            @Param("serviceId") Long serviceId,
            @Param("metricName") String metricName,
            @Param("unit") String unit,
            @Param("recordedAt") Instant recordedAt,
            Pageable pageable
    );
}
