package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ESoccerPredictionRepository extends JpaRepository<ESoccerPrediction, Long> {
    Optional<ESoccerPrediction> findByMatchId(Long matchId);
    List<ESoccerPrediction> findByActualResultIsNotNull();
    List<ESoccerPrediction> findByConfidence(ESoccerPrediction.ConfidenceLevel confidence);
}
