package com.betanalyzer.esoccer.dto;

import java.util.List;

public record PlayerFormResponse(
        String playerName, String gamerId,
        int matchesConsidered,
        int wins, int draws, int losses,
        double winPercentage,
        double avgGoalsScored, double avgGoalsConceded,
        double avgTotalGoals, double avgGoalDifference,
        double over1_5Frequency, double over2_5Frequency, double bttsFrequency,
        boolean losingStreak, boolean highScoringGames,
        List<MatchSummaryDto> lastMatches
) {
    public record MatchSummaryDto(
            String opponent, String selectedTeam, String result,
            int goalsScored, int goalsConceded, String matchDate,
            String competition, String gameMode, Integer durationMinutes
    ) {}
}
