package com.betanalyzer.service;

import com.betanalyzer.entity.SlipSelection.Market;
import com.betanalyzer.entity.Team;
import org.springframework.stereotype.Service;

/**
 * Core statistical model.
 *
 * Match-result probabilities (home/draw/away) come from an Elo-style
 * rating difference (with home advantage and short-term form baked in)
 * passed through a logistic curve, then split into home/draw/away using
 * a draw-probability curve that shrinks as the rating gap widens
 * (blowout matches produce fewer draws - well supported empirically).
 *
 * Goal-market probabilities (over/under, BTTS) come from a Poisson
 * goal-expectancy model built from each side's scoring/conceding
 * averages - the standard approach used across football analytics.
 */
@Service
public class ProbabilityCalculationService {

    private static final double NEUTRAL_FORM = 7.5; // midpoint of 0-15
    private static final double FORM_WEIGHT = 8.0;  // elo points per form-point of deviation
    private static final double HOME_GOAL_BOOST = 1.10;
    private static final double AWAY_GOAL_DEFLATE = 0.92;

    public record MatchOutcomeProbabilities(double homeWin, double draw, double awayWin) {}

    public MatchOutcomeProbabilities computeOutcomeProbabilities(Team home, Team away) {
        double formAdjHome = (home.getFormPoints() - NEUTRAL_FORM) * FORM_WEIGHT;
        double formAdjAway = (away.getFormPoints() - NEUTRAL_FORM) * FORM_WEIGHT;

        double diff = (home.getEloRating() + home.getHomeAdvantage() + formAdjHome)
                - (away.getEloRating() + formAdjAway);

        double raw = 1.0 / (1.0 + Math.pow(10, -diff / 400.0));

        // Draw probability peaks (~28%) for evenly matched sides and
        // shrinks as the gap grows, floored at 16% (very rarely lower in practice).
        double drawProb = Math.max(0.16, 0.28 - Math.abs(diff) / 1000.0);
        double remaining = 1 - drawProb;

        double homeWin = remaining * raw;
        double awayWin = remaining * (1 - raw);

        return new MatchOutcomeProbabilities(homeWin, drawProb, awayWin);
    }

    public double computeMarketProbability(Team home, Team away, Market market) {
        MatchOutcomeProbabilities outcome = computeOutcomeProbabilities(home, away);
        return switch (market) {
            case HOME_WIN -> outcome.homeWin();
            case DRAW -> outcome.draw();
            case AWAY_WIN -> outcome.awayWin();
            case OVER_2_5 -> probabilityOverGoals(home, away, 2);
            case UNDER_2_5 -> 1 - probabilityOverGoals(home, away, 2);
            case BTTS_YES -> probabilityBttsYes(home, away);
            case BTTS_NO -> 1 - probabilityBttsYes(home, away);
            case UNKNOWN -> 0.5; // no model applies - neutral fallback
        };
    }

    /** Expected goals for the home side in this fixture. */
    public double expectedHomeGoals(Team home, Team away) {
        return ((home.getGoalsForAvg() + away.getGoalsAgainstAvg()) / 2.0) * HOME_GOAL_BOOST;
    }

    /** Expected goals for the away side in this fixture. */
    public double expectedAwayGoals(Team home, Team away) {
        return ((away.getGoalsForAvg() + home.getGoalsAgainstAvg()) / 2.0) * AWAY_GOAL_DEFLATE;
    }

    /** P(total match goals > thresholdGoals + 0.5), e.g. thresholdGoals=2 -> Over 2.5. */
    public double probabilityOverGoals(Team home, Team away, int thresholdGoals) {
        double lh = expectedHomeGoals(home, away);
        double la = expectedAwayGoals(home, away);

        double pUpToThreshold = 0.0;
        for (int h = 0; h <= thresholdGoals; h++) {
            for (int a = 0; a <= thresholdGoals - h; a++) {
                pUpToThreshold += poissonPmf(h, lh) * poissonPmf(a, la);
            }
        }
        return clamp(1 - pUpToThreshold);
    }

    public double probabilityBttsYes(Team home, Team away) {
        double lh = expectedHomeGoals(home, away);
        double la = expectedAwayGoals(home, away);
        double pHomeBlank = poissonPmf(0, lh);
        double pAwayBlank = poissonPmf(0, la);
        double pNeitherScores = pHomeBlank * pAwayBlank;
        double pBttsNo = pHomeBlank + pAwayBlank - pNeitherScores;
        return clamp(1 - pBttsNo);
    }

    private double poissonPmf(int k, double lambda) {
        return Math.exp(-lambda) * Math.pow(lambda, k) / factorial(k);
    }

    private long factorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    private double clamp(double p) {
        return Math.max(0.0, Math.min(1.0, p));
    }
}
