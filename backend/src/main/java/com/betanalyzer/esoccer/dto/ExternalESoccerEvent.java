package com.betanalyzer.esoccer.dto;

import java.time.Instant;

/**
 * Provider-neutral eSoccer event, before it becomes an ESoccerMatch entity.
 */
public record ExternalESoccerEvent(
        String externalId,
        String competition,
        ExternalESoccerTeamRef playerA,
        ExternalESoccerTeamRef playerB,
        String teamAName,   // football club selected in-game by player A
        String teamBName,   // football club selected in-game by player B
        String gameMode,
        Instant matchDateTime,
        Integer durationMinutes,
        Integer goalsA,
        Integer goalsB,
        String status,
        boolean finished,
        boolean cancelled
) {
    public boolean isUsable() {
        return externalId != null
                && playerA != null && playerB != null
                && matchDateTime != null
                && playerA.externalId() != null && playerB.externalId() != null;
    }
}
