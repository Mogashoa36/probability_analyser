package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ESoccerMatchRepository extends JpaRepository<ESoccerMatch, Long> {

    List<ESoccerMatch> findByPlayerAIdOrderByMatchDateTimeDesc(Long playerId);
    List<ESoccerMatch> findByPlayerBIdOrderByMatchDateTimeDesc(Long playerId);

    /** Completed matches involving a player, most recent first. */
    @Query("SELECT m FROM ESoccerMatch m WHERE (m.playerA.id = :playerId OR m.playerB.id = :playerId) " +
           "AND m.status = 'PLAYED' AND m.goalsA IS NOT NULL AND m.goalsB IS NOT NULL " +
           "ORDER BY m.matchDateTime DESC")
    List<ESoccerMatch> findCompletedByPlayer(@Param("playerId") Long playerId);

    /** Completed head-to-head matches between two players, most recent first. */
    @Query("SELECT m FROM ESoccerMatch m WHERE " +
           "((m.playerA.id = :playerAId AND m.playerB.id = :playerBId) OR " +
           "(m.playerA.id = :playerBId AND m.playerB.id = :playerAId)) " +
           "AND m.status = 'PLAYED' AND m.goalsA IS NOT NULL AND m.goalsB IS NOT NULL " +
           "ORDER BY m.matchDateTime DESC")
    List<ESoccerMatch> findHeadToHead(@Param("playerAId") Long playerAId,
                                      @Param("playerBId") Long playerBId);

    List<ESoccerMatch> findByCompetitionId(Long competitionId);
    List<ESoccerMatch> findByStatus(ESoccerMatch.MatchStatus status);
    List<ESoccerMatch> findByStatusAndMatchDateTimeAfter(ESoccerMatch.MatchStatus status, LocalDateTime after);
    Optional<ESoccerMatch> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);
    long countByStatus(ESoccerMatch.MatchStatus status);
    long countByPlayerId(Long playerId);
}
