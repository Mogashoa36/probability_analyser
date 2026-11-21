package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerGameMode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ESoccerGameModeRepository extends JpaRepository<ESoccerGameMode, Long> {
    Optional<ESoccerGameMode> findByNameIgnoreCase(String name);
    Optional<ESoccerGameMode> findByExternalId(String externalId);
    List<ESoccerGameMode> findByAllowDraws(boolean allowDraws);
}
