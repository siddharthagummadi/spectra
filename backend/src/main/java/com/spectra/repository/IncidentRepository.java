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

    List<Incident> findByAlertIdIn(List<Long> alertIds);

    boolean existsByServiceIdAndStatusIn(Long serviceId, List<String> statuses);

    boolean existsByServiceIdAndTitleIgnoreCaseAndStatusIn(Long serviceId, String title, List<String> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Incident> findByServiceEnterpriseIdOrderByTriggeredAtDescIdDesc(Long enterpriseId);

    List<Incident> findByServiceEnterpriseIdAndStatusOrderByTriggeredAtDescIdDesc(Long enterpriseId, String status);

    List<Incident> findByServiceIdAndServiceEnterpriseIdOrderByTriggeredAtDescIdDesc(Long serviceId, Long enterpriseId);

    List<Incident> findByServiceIdAndServiceEnterpriseIdAndStatusOrderByTriggeredAtDescIdDesc(Long serviceId, Long enterpriseId, String status);

    Optional<Incident> findByIdAndServiceEnterpriseId(Long id, Long enterpriseId);

    boolean existsByIdAndServiceEnterpriseId(Long id, Long enterpriseId);

    long countByServiceEnterpriseIdAndStatusIn(Long enterpriseId, List<String> statuses);

    long countByStatusIn(List<String> statuses);
}
