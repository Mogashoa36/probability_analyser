package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ESoccerTeamRepository extends JpaRepository<ESoccerTeam, Long> {
    Optional<ESoccerTeam> findByNameIgnoreCase(String name);
    Optional<ESoccerTeam> findByExternalId(String externalId);
}
