package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByOrderByTriggeredAtDescIdDesc();

    List<Incident> findByServiceUserIdOrderByTriggeredAtDescIdDesc(Long userId);

    List<Incident> findByStatusOrderByTriggeredAtDescIdDesc(String status);

    List<Incident> findByServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(Long userId, String status);

    List<Incident> findByServiceIdOrderByTriggeredAtDescIdDesc(Long serviceId);

    List<Incident> findByServiceIdAndServiceUserIdOrderByTriggeredAtDescIdDesc(Long serviceId, Long userId);

    List<Incident> findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(Long serviceId, String status);

    List<Incident> findByServiceIdAndServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(Long serviceId, Long userId, String status);

    Optional<Incident> findByIdAndServiceUserId(Long id, Long userId);

    boolean existsByIdAndServiceUserId(Long id, Long userId);

    boolean existsByAlertIdAndStatusIn(Long alertId, List<String> statuses);

    boolean existsByServiceIdAndTitleIgnoreCaseAndStatusIn(Long serviceId, String title, List<String> statuses);
}
