package com.betanalyzer.esoccer.service;

import java.util.List;

/**
 * Aggregated statistics for a player based on their last N completed matches.
 */
public record PlayerFormStats(
        String playerName,
        String gamerId,
        int matchesConsidered,
        int wins, int draws, int losses,
        double winPercentage,
        double avgGoalsScored,
        double avgGoalsConceded,
        double avgTotalGoals,
        double avgGoalDifference,
        double over1_5Frequency,
        double over2_5Frequency,
        double bttsFrequency,
        List<MatchSummary> lastMatches,
        boolean losingStreak,
        boolean highScoringGames
) {
    public record MatchSummary(
            String opponent, String selectedTeam, String result,
            int goalsScored, int goalsConceded, String matchDate,
            String competition, String gameMode, Integer durationMinutes
    ) {}
}
