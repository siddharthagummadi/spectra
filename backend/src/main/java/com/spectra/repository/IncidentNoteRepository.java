package com.spectra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.IncidentNote;

public interface IncidentNoteRepository extends JpaRepository<IncidentNote, Long> {

    List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    List<IncidentNote> findByIncidentIdAndIncidentServiceUserIdOrderByCreatedAtAsc(Long incidentId, Long userId);
}
