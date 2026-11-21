package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerCompetition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ESoccerCompetitionRepository extends JpaRepository<ESoccerCompetition, Long> {
    Optional<ESoccerCompetition> findByNameIgnoreCase(String name);
    Optional<ESoccerCompetition> findByExternalId(String externalId);
    List<ESoccerCompetition> findByAllowDraws(boolean allowDraws);
}
