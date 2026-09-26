package com.betanalyzer.repository;

import com.betanalyzer.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByNameIgnoreCase(String name);

    /** Used by the external-data importer to re-attach to an already-known club. */
    Optional<Team> findByExternalId(String externalId);

    long countByExternalIdIsNotNull();
}
