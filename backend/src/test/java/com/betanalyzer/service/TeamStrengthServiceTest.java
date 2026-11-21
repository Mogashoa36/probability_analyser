package com.betanalyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;

import com.betanalyzer.entity.Match;
import com.betanalyzer.entity.Team;
import com.betanalyzer.repository.TeamAliasRepository;
import com.betanalyzer.repository.TeamRepository;
import org.junit.jupiter.api.Test;

/**
 * Validates the Elo / form / goal-average update logic applied after a match
 * is recorded. Ratings must move in the correct direction, stay bounded, and
 * never double-count a result.
 */
class TeamStrengthServiceTest {

    private static final double K = 20.0;

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamAliasRepository teamAliasRepository = mock(TeamAliasRepository.class);

    private Team team(String name, double elo) {
        Team t = new Team(name, "League", elo);
        t.setFormPoints(0);
        t.setGoalsForAvg(1.2);
        t.setGoalsAgainstAvg(1.2);
        t.setHomeAdvantage(55);
        return t;
    }

    private Match match(Team home, Team away, Integer homeGoals, Integer awayGoals) {
        Match m = new Match();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setHomeGoals(homeGoals);
        m.setAwayGoals(awayGoals);
        m.setStatus(Match.MatchStatus.PLAYED);
        return m;
    }

    private TeamStrengthService svc() {
        return new TeamStrengthService(teamRepository, teamAliasRepository);
    }

    @Test
    void homeWinMovesHomeEloUpAndAwayEloDown() {
        Team home = team("Home", 1500);
        Team away = team("Away", 1500);
        home.setHomeAdvantage(55);

        double expectedHome = 1.0 / (1.0 + Math.pow(10, -((1500 + 55) - 1500) / 400.0));
        svc().recordResult(match(home, away, 1, 0));

        assertThat(home.getEloRating()).isCloseTo(1500 + K * (1.0 - expectedHome), offset(0.01));
        assertThat(away.getEloRating()).isCloseTo(1500 + K * (0.0 - (1.0 - expectedHome)), offset(0.01));
        assertThat(home.getEloRating()).isGreaterThan(1500);
        assertThat(away.getEloRating()).isLessThan(1500);
    }

    @Test
    void drawSplitsPoints() {
        Team home = team("Home", 1500);
        Team away = team("Away", 1500);
        home.setHomeAdvantage(55);

        double expectedHome = 1.0 / (1.0 + Math.pow(10, -((1500 + 55) - 1500) / 400.0));
        svc().recordResult(match(home, away, 1, 1));

        assertThat(home.getEloRating()).isCloseTo(1500 + K * (0.5 - expectedHome), offset(0.01));
        assertThat(away.getEloRating()).isCloseTo(1500 + K * (0.5 - (1.0 - expectedHome)), offset(0.01));
    }

    @Test
    void formPointsUseWinDrawLossAndClamp() {
        Team home = team("Home", 1500);
        Team away = team("Away", 1500);
        home.setFormPoints(12);
        away.setFormPoints(1);
        home.setHomeAdvantage(55);

        svc().recordResult(match(home, away, 1, 0));

        // Home wins: decay 12*0.8 + 3 = 12.6 -> 13, capped at 15.
        assertThat(home.getFormPoints()).isEqualTo(13);
        // Away loses: decay 1*0.8 + 0 = 0.8 -> 1.
        assertThat(away.getFormPoints()).isEqualTo(1);
    }

    @Test
    void goalAveragesUseEma() {
        Team home = team("Home", 1500);
        Team away = team("Away", 1500);
        home.setHomeAdvantage(55);

        svc().recordResult(match(home, away, 2, 1));

        // Home GF: 1.2 + 0.2*(2 - 1.2) = 1.36
        assertThat(home.getGoalsForAvg()).isCloseTo(1.36, offset(0.001));
        // Home GA: 1.2 + 0.2*(1 - 1.2) = 1.16
        assertThat(home.getGoalsAgainstAvg()).isCloseTo(1.16, offset(0.001));
        // Away GF: 1.2 + 0.2*(1 - 1.2) = 1.16
        assertThat(away.getGoalsForAvg()).isCloseTo(1.16, offset(0.001));
        // Away GA: 1.2 + 0.2*(2 - 1.2) = 1.36
        assertThat(away.getGoalsAgainstAvg()).isCloseTo(1.36, offset(0.001));
    }

    @Test
    void recordingResultWithNullGoalsThrows() {
        Team home = team("Home", 1500);
        Team away = team("Away", 1500);

        assertThatThrownBy(() -> svc().recordResult(match(home, away, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

