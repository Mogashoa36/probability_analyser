package com.betanalyzer.esoccer.service;

import java.util.List;

/**
 * Head-to-head statistics between two esports players.
 */
public record HeadToHeadStats(
        String playerA, String playerB,
        int encounters,
        int playerAWins, int playerBWins, int draws,
        double avgGoalsPerEncounter,
        String mostFrequentScoreline,
        int highestScoringEncounter,
        int lowestScoringEncounter,
        double over1_5Percentage,
        double over2_5Percentage,
        double bttsPercentage,
        int consecutiveWinsAgainstOpponent, // positive = A leads, negative = B leads
        List<MatchSummary> encountersList,
        boolean sampleSizeWarning
) {
    public record MatchSummary(
            String playerA, String playerB, int goalsA, int goalsB,
            String result, String matchDate, String competition
    ) {}
}
