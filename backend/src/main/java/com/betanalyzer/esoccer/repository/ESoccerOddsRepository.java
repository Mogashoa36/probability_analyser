package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerOdds;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ESoccerOddsRepository extends JpaRepository<ESoccerOdds, Long> {
    List<ESoccerOdds> findByMatchId(Long matchId);
    Optional<ESoccerOdds> findByMatchIdAndMarketAndOutcome(Long matchId, String market, String outcome);
    boolean existsByExternalId(String externalId);
}
