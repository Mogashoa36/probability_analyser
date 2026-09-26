package com.betanalyzer.repository;

import com.betanalyzer.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Fallback lookup for the importer when a provider fixture has no external
     * id on file yet (for example it matches a hand-entered fixture). The
     * kick-off day is compared in the service, since it is a timestamp here.
     */
    List<Match> findByHomeTeamNameIgnoreCaseAndAwayTeamNameIgnoreCase(String homeName, String awayName);

    List<Match> findByStatus(Match.MatchStatus status);

    /** Used by the external-data importer so a re-sync updates rather than duplicates. */
    Optional<Match> findByExternalId(String externalId);

    long countByStatus(Match.MatchStatus status);
}
