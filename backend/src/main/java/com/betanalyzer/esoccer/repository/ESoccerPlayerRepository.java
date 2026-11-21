package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ESoccerPlayerRepository extends JpaRepository<ESoccerPlayer, Long> {
    Optional<ESoccerPlayer> findByGamerIdIgnoreCase(String gamerId);
    Optional<ESoccerPlayer> findByExternalId(String externalId);
    boolean existsByGamerIdIgnoreCase(String gamerId);
}
