package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.entity.ESoccerGameMode;
import com.betanalyzer.esoccer.entity.ESoccerPlayerRating;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRatingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Core eSoccer probability model.
 *
 * <p>Approach:<br>
 * 1. Elo-style rating per esports player (separate from football team ratings).<br>
 * 2. Recency-weighted recent form (last 5 matches, heavier weight on recent).<br>
 * 3. Head-to-head adjustment from direct encounters.<br>
 * 4. Poisson goal modelling for over/under and BTTS markets.<br>
 * 5. Game-mode factors (different scoring patterns per game/duration).<br>
 *
 * <p>Draws are only offered when the competition/game mode permits them.
 */
@Slf4j
@Service
public class ESoccerProbabilityCalculationService {

    private static final double BASE_RATING = 1500.0;
    private static final double FORM_WEIGHT = 6.0;     // rating points per form-point deviation
    private static final double H2H_WEIGHT = 4.0;      // rating points per H2H win advantage
    private static final double GOAL_AVG_ALPHA = 0.25; // EMA weight for goal averages
    private static final double HOME_FIELD_ADVANTAGE = 0.0; // eSoccer is typically neutral venue

    private final ESoccerPlayerRatingRepository ratingRepository;

    public ESoccerProbabilityCalculationService(ESoccerPlayerRatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    /** Outcome probabilities (Player A win / draw / Player B win). */
    @Transactional(readOnly = true)
    public OutcomeProbabilities computeOutcomeProbabilities(
            ESoccerPlayerRating ratingA, ESoccerPlayerRating ratingB,
            PlayerFormStats formA, PlayerFormStats formB,
            HeadToHeadStats h2h, ESoccerGameMode gameMode) {

        double ratingDiff = ratingA.getRating() - ratingB.getRating();

        // Form adjustment: convert 0-100 form score to rating deviation
        double formAdjA = (formA.winPercentage() - 50.0) / 100.0 * FORM_WEIGHT * 20;
        double formAdjB = (formB.winPercentage() - 50.0) / 100.0 * FORM_WEIGHT * 20;

        // Head-to-head adjustment
        double h2hAdj = 0.0;
        if (h2h.encounters() > 0) {
            double h2hEdge = (double) (h2h.playerAWins() - h2h.playerBWins()) / Math.max(1, h2h.encounters());
            h2hAdj = h2hEdge * H2H_WEIGHT * 10;
            // Reduce H2H weight for small samples
            double sampleFactor = Math.min(1.0, h2h.encounters() / 5.0);
            h2hAdj *= sampleFactor;
        }

        double totalDiff = ratingDiff + formAdjA - formAdjB + h2hAdj;

        // Logistic curve for win probability
        double rawA = 1.0 / (1.0 + Math.pow(10, -totalDiff / 400.0));

        // Draw probability: peaks for even matches, shrinks with gap
        // Only applicable if game mode allows draws
        double drawProb = 0.0;
        if (gameMode != null && gameMode.isAllowDraws()) {
            drawProb = Math.max(0.08, 0.26 - Math.abs(totalDiff) / 900.0);
        }

        double remaining = 1.0 - drawProb;
        double probA = clamp(remaining * rawA);
        double probB = clamp(remaining * (1.0 - rawA));
        drawProb = clamp(drawProb);

        // Renormalise to ensure sum = 1
        double sum = probA + drawProb + probB;
        if (sum > 0) {
            probA /= sum;
            drawProb /= sum;
            probB /= sum;
        }

        return new OutcomeProbabilities(probA, drawProb, probB, totalDiff);
    }

    /** Goal-market probabilities using Poisson modelling. */
    @Transactional(readOnly = true)
    public GoalMarketProbabilities computeGoalProbabilities(
            PlayerFormStats formA, PlayerFormStats formB,
            HeadToHeadStats h2h, ESoccerGameMode gameMode) {

        double lambdaA = formA.avgGoalsScored();
        double lambdaB = formB.avgGoalsScored();

        // Blend in H2H scoring history when available
        if (h2h.encounters() > 0) {
            double h2hWeight = Math.min(0.3, h2h.encounters() / 10.0);
            double h2hAvgA = h2hAvgGoalsFor(h2h, true);
            double h2hAvgB = h2hAvgGoalsFor(h2h, false);
            lambdaA = lambdaA * (1 - h2hWeight) + h2hAvgA * h2hWeight;
            lambdaB = lambdaB * (1 - h2hWeight) + h2hAvgB * h2hWeight;
        }

        // Game mode prior when data is thin
        if (gameMode != null && gameMode.getAvgTotalGoals() > 0) {
            double modePriorWeight = dataWeight(formA.matchesConsidered() + formB.matchesConsidered());
            double modeAvgPerPlayer = gameMode.getAvgTotalGoals() / 2.0;
            lambdaA = lambdaA * (1 - modePriorWeight) + modeAvgPerPlayer * modePriorWeight;
            lambdaB = lambdaB * (1 - modePriorWeight) + modeAvgPerPlayer * modePriorWeight;
        }

        lambdaA = Math.max(lambdaA, 0.1);
        lambdaB = Math.max(lambdaB, 0.1);

        double over1_5 = probabilityOverGoals(lambdaA, lambdaB, 1);
        double over2_5 = probabilityOverGoals(lambdaA, lambdaB, 2);
        double over3_5 = probabilityOverGoals(lambdaA, lambdaB, 3);
        double under2_5 = 1.0 - over2_5;
        double bttsYes = probabilityBttsYes(lambdaA, lambdaB);

        return new GoalMarketProbabilities(
                lambdaA, lambdaB,
                clamp(over1_5), clamp(over2_5), clamp(over3_5),
                clamp(under2_5), clamp(bttsYes)
        );
    }

    /** Double chance probabilities from outcome probabilities. */
    public DoubleChanceProbabilities computeDoubleChance(OutcomeProbabilities outcome) {
        double prob1X = clamp(outcome.probPlayerAWin() + outcome.probDraw());
        double probX2 = clamp(outcome.probDraw() + outcome.probPlayerBWin());
        double prob12 = clamp(outcome.probPlayerAWin() + outcome.probPlayerBWin());
        return new DoubleChanceProbabilities(prob1X, probX2, prob12);
    }

    // --------------------------------------------------------------- private

    private double probabilityOverGoals(double lambdaA, double lambdaB, int threshold) {
        double pUpTo = 0.0;
        for (int h = 0; h <= threshold; h++) {
            for (int a = 0; a <= threshold - h; a++) {
                pUpTo += poissonPmf(h, lambdaA) * poissonPmf(a, lambdaB);
            }
        }
        return 1.0 - pUpTo;
    }

    private double probabilityBttsYes(double lambdaA, double lambdaB) {
        double pHomeBlank = poissonPmf(0, lambdaA);
        double pAwayBlank = poissonPmf(0, lambdaB);
        double pNeither = pHomeBlank * pAwayBlank;
        double pBttsNo = pHomeBlank + pAwayBlank - pNeither;
        return 1.0 - pBttsNo;
    }

    private double poissonPmf(int k, double lambda) {
        return Math.exp(-lambda) * Math.pow(lambda, k) / factorial(k);
    }

    private long factorial(int n) {
        long r = 1;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }

    /** Less weight on game-mode prior when we have lots of data. */
    private double dataWeight(int totalMatches) {
        return Math.max(0.1, 1.0 - totalMatches / 20.0);
    }

    private double h2hAvgGoalsFor(HeadToHeadStats h2h, boolean forPlayerA) {
        if (h2h.encountersList() == null || h2h.encountersList().isEmpty()) return 0.0;
        int total = 0;
        for (var m : h2h.encountersList()) {
            total += forPlayerA ? m.goalsA() : m.goalsB();
        }
        return (double) total / h2h.encountersList().size();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // --------------------------------------------------------------- records

    public record OutcomeProbabilities(
            double probPlayerAWin, double probDraw, double probPlayerBWin, double ratingDiff) {}

    public record GoalMarketProbabilities(
            double lambdaA, double lambdaB,
            double probOver1_5, double probOver2_5, double probOver3_5,
            double probUnder2_5, double probBttsYes) {}

    public record DoubleChanceProbabilities(
            double prob1X, double probX2, double prob12) {}
}
