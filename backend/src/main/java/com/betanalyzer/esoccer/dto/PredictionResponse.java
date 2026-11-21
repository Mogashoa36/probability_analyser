package com.betanalyzer.esoccer.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full prediction response for an upcoming eSoccer match.
 */
public record PredictionResponse(
        Long matchId,
        String playerA, String playerB,
        String teamA, String teamB,
        String competition, String gameMode,
        LocalDateTime matchDateTime,
        Integer durationMinutes,
        OutcomeProbabilitiesDto outcome,
        GoalMarketProbabilitiesDto goals,
        DoubleChanceProbabilitiesDto doubleChance,
        ConfidenceDto confidence,
        String explanation,
        String supportingStatistics,
        int sampleSize,
        OddsComparisonDto oddsComparison
) {
    public record OutcomeProbabilitiesDto(
            Double probPlayerAWin, Double probDraw, Double probPlayerBWin, boolean drawApplicable) {}
    public record GoalMarketProbabilitiesDto(
            Double probOver1_5, Double probOver2_5, Double probOver3_5,
            Double probUnder2_5, Double probBttsYes) {}
    public record DoubleChanceProbabilitiesDto(
            Double prob1X, Double probX2, Double prob12, boolean drawApplicable) {}
    public record ConfidenceDto(
            String level, double score, List<String> factors) {}
    public record OddsComparisonDto(
            String market, String outcome, Double bookmakerOdds,
            Double impliedProbability, Double modelProbability,
            Double difference, Double expectedValue, boolean valueBet) {}
}
