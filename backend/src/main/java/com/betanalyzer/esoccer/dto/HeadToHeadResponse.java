package com.betanalyzer.esoccer.dto;

import java.util.List;

public record HeadToHeadResponse(
        String playerA, String playerB,
        int encounters, int playerAWins, int playerBWins, int draws,
        double avgGoalsPerEncounter, String mostFrequentScoreline,
        int highestScoringEncounter, int lowestScoringEncounter,
        double over1_5Percentage, double over2_5Percentage, double bttsPercentage,
        int consecutiveWinsAgainstOpponent,
        boolean sampleSizeWarning,
        List<EncounterDto> encounterList
) {
    public record EncounterDto(
            String playerA, String playerB, int goalsA, int goalsB,
            String result, String matchDate, String competition
    ) {}
}
