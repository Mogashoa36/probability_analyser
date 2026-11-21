package com.betanalyzer.esoccer.dto;

import java.time.LocalDateTime;

public record MatchSummaryDto(
        Long id, String playerA, String playerB, String teamA, String teamB,
        String competition, String gameMode, LocalDateTime matchDateTime,
        String status, Integer goalsA, Integer goalsB
) {}
