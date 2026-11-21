package com.betanalyzer.esoccer.dto;

import java.time.LocalDateTime;

public record CreateMatchRequest(
        String playerAGamerId, String playerBGamerId,
        String teamAName, String teamBName,
        String competitionName, String gameModeName,
        LocalDateTime matchDateTime, Integer durationMinutes,
        String externalId
) {}
