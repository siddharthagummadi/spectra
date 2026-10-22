package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.MonitoredService;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    Optional<MonitoredService> findByNameIgnoreCase(String name);

    Optional<MonitoredService> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT s FROM MonitoredService s LEFT JOIN FETCH s.user WHERE s.id = :id")
    Optional<MonitoredService> findByIdWithUser(@Param("id") Long id);

    @Query("SELECT s FROM MonitoredService s LEFT JOIN FETCH s.user WHERE s.id = :id AND s.user.id = :userId")
    Optional<MonitoredService> findByIdAndUserIdWithUser(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    List<MonitoredService> findAllByOrderByCreatedAtDesc();

    @Query("SELECT s FROM MonitoredService s LEFT JOIN FETCH s.user ORDER BY s.createdAt DESC")
    List<MonitoredService> findAllWithUserByOrderByCreatedAtDesc();

    List<MonitoredService> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT s FROM MonitoredService s LEFT JOIN FETCH s.user WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<MonitoredService> findByUserIdWithUserOrderByCreatedAtDesc(@Param("userId") Long userId);
}
