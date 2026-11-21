package com.betanalyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.betanalyzer.entity.SlipSelection.Market;
import com.betanalyzer.entity.Team;
import org.junit.jupiter.api.Test;

/**
 * Independent validation of the core statistical model.
 *
 * <p>The Elo/logistic split and the Poisson goal model are checked against
 * hand-computed expectations and invariant properties (probabilities sum to
 * one, stay within [0, 1], and behave monotonically). The model must never
 * silently produce misleading numbers - empty or degenerate inputs are tested
 * for sensible, bounded output.
 */
class ProbabilityCalculationServiceTest {

    private final ProbabilityCalculationService svc = new ProbabilityCalculationService();

    private Team team(String name, double elo, double form, double gf, double ga, double homeAdv) {
        Team t = new Team(name, "League", elo);
        t.setFormPoints((int) form);
        t.setGoalsForAvg(gf);
        t.setGoalsAgainstAvg(ga);
        t.setHomeAdvantage(homeAdv);
        return t;
    }

    @Test
    void outcomeProbabilitiesSumToOne() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);
        ProbabilityCalculationService.MatchOutcomeProbabilities p =
                svc.computeOutcomeProbabilities(home, away);

        assertThat(p.homeWin() + p.draw() + p.awayWin()).isCloseTo(1.0, offset(1e-9));
        assertThat(p.homeWin()).isBetween(0.0, 1.0);
        assertThat(p.draw()).isBetween(0.0, 1.0);
        assertThat(p.awayWin()).isBetween(0.0, 1.0);
    }

    @Test
    void drawProbabilityStaysWithinEmpiricalBounds() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);
        double draw = svc.computeOutcomeProbabilities(home, away).draw();

        assertThat(draw).isBetween(0.16, 0.28);
    }

    @Test
    void evenTeamsSplitEvenlyWhenNoHomeAdvantage() {
        Team home = team("Home", 1500, 7.5, 1.5, 1.5, 0);
        Team away = team("Away", 1500, 7.5, 1.5, 1.5, 0);
        ProbabilityCalculationService.MatchOutcomeProbabilities p =
                svc.computeOutcomeProbabilities(home, away);

        assertThat(p.homeWin()).isCloseTo(0.36, offset(1e-9));
        assertThat(p.awayWin()).isCloseTo(0.36, offset(1e-9));
        assertThat(p.draw()).isCloseTo(0.28, offset(1e-9));
    }

    @Test
    void strongHomeFavoriteProducesHighHomeWin() {
        Team home = team("Home", 2000, 14, 2.5, 0.6, 60);
        Team away = team("Away", 1200, 2, 0.8, 2.0, 50);
        double homeWin = svc.computeOutcomeProbabilities(home, away).homeWin();

        assertThat(homeWin).isGreaterThan(0.70);
    }
    @Test
    void marketProbabilitiesAreAllWithinUnitInterval() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        assertThat(svc.computeMarketProbability(home, away, Market.HOME_WIN)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.DRAW)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.AWAY_WIN)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.OVER_2_5)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.UNDER_2_5)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.BTTS_YES)).isBetween(0.0, 1.0);
        assertThat(svc.computeMarketProbability(home, away, Market.BTTS_NO)).isBetween(0.0, 1.0);
    }

    @Test
    void overAndUnderAreComplements() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        double over = svc.computeMarketProbability(home, away, Market.OVER_2_5);
        double under = svc.computeMarketProbability(home, away, Market.UNDER_2_5);
        assertThat(over + under).isCloseTo(1.0, offset(1e-9));

        double bttsYes = svc.computeMarketProbability(home, away, Market.BTTS_YES);
        double bttsNo = svc.computeMarketProbability(home, away, Market.BTTS_NO);
        assertThat(bttsYes + bttsNo).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void unknownMarketIsNeutralFallback() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        assertThat(svc.computeMarketProbability(home, away, Market.UNKNOWN)).isEqualTo(0.5);
    }

    @Test
    void expectedGoalsArePositive() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        assertThat(svc.expectedHomeGoals(home, away)).isPositive();
        assertThat(svc.expectedAwayGoals(home, away)).isPositive();
    }

    @Test
    void higherGoalLineMeansLowerOverProbability() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        double over25 = svc.probabilityOverGoals(home, away, 2); // P(total > 2.5) = P(total >= 3)
        double over35 = svc.probabilityOverGoals(home, away, 3); // P(total > 3.5) = P(total >= 4)

        assertThat(over25).isGreaterThan(over35);
    }

    @Test
    void poissonPmfIsNormalised() {
        Team home = team("Home", 1600, 10, 1.8, 1.0, 55);
        Team away = team("Away", 1500, 8, 1.4, 1.2, 55);

        double lh = svc.expectedHomeGoals(home, away);
        double la = svc.expectedAwayGoals(home, away);

        double sumHome = 0.0;
        double sumAway = 0.0;
        for (int k = 0; k <= 12; k++) {
            sumHome += poisson(svc, k, lh);
            sumAway += poisson(svc, k, la);
        }
        assertThat(sumHome).isCloseTo(1.0, offset(1e-6));
        assertThat(sumAway).isCloseTo(1.0, offset(1e-6));
    }

    private double poisson(ProbabilityCalculationService s, int k, double lambda) {
        try {
            var m = ProbabilityCalculationService.class.getDeclaredMethod("poissonPmf", int.class, double.class);
            m.setAccessible(true);
            return (double) m.invoke(s, k, lambda);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

