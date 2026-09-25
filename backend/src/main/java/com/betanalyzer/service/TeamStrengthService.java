package com.betanalyzer.service;

import com.betanalyzer.entity.Match;
import com.betanalyzer.entity.Team;
import com.betanalyzer.entity.TeamAlias;
import com.betanalyzer.repository.TeamAliasRepository;
import com.betanalyzer.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves teams by name (falling back to a league-average profile for
 * unknown teams so the system never hard-fails on an unrecognised name)
 * and updates Elo/form/goal-average figures once a match is confirmed played.
 */
@Service
@RequiredArgsConstructor
public class TeamStrengthService {

    private static final double K_FACTOR = 20.0;      // Elo learning rate
    private static final double GOAL_AVG_ALPHA = 0.2;  // exponential moving average weight
    private static final double FORM_DECAY = 0.8;

    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;

    /** Looks up a team by (fuzzy, case-insensitive) name; returns a transient
     *  league-average team if it isn't in the database yet. */
    public Team resolveOrDefault(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) return fallbackTeam("Unknown Team");

        return findKnown(cleaned).orElseGet(() -> fallbackTeam(cleaned));
    }

    /**
     * Resolution order: exact name, then a known nickname, then a punctuation-
     * and spacing-insensitive form of either. The nickname step matters because
     * bookmakers print "Man City"/"Barca" rather than the official club name, and
     * a miss there silently downgrades the leg to league-average figures.
     */
    private Optional<Team> findKnown(String name) {
        Optional<Team> byName = teamRepository.findByNameIgnoreCase(name);
        if (byName.isPresent()) return byName;

        Optional<Team> byAlias = teamAliasRepository.findByAliasNameIgnoreCase(name)
                .map(TeamAlias::getTeam);
        if (byAlias.isPresent()) return byAlias;

        String normalized = normalize(name);
        if (!normalized.equalsIgnoreCase(name)) {
            Optional<Team> byNormalizedName = teamRepository.findByNameIgnoreCase(normalized);
            if (byNormalizedName.isPresent()) return byNormalizedName;

            Optional<Team> byNormalizedAlias = teamAliasRepository.findByAliasNameIgnoreCase(normalized)
                    .map(TeamAlias::getTeam);
            if (byNormalizedAlias.isPresent()) return byNormalizedAlias;
        }
        return Optional.empty();
    }

    /** Lower-cases and strips punctuation so "Man. City" and "man  city" both match. */
    private String normalize(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private Team fallbackTeam(String name) {
        Team fallback = new Team(name, "Unranked", 1500);
        fallback.setFormPoints(7);
        fallback.setGoalsForAvg(1.2);
        fallback.setGoalsAgainstAvg(1.2);
        fallback.setHomeAdvantage(55);
        return fallback;
    }

    /** True only if the team actually exists in the database (i.e. we have real history on them). */
    public boolean isKnownTeam(String name) {
        String cleaned = name == null ? "" : name.trim();
        return !cleaned.isEmpty() && findKnown(cleaned).isPresent();
    }

    /** Apply a completed match's result to both teams' ratings, form and goal averages. */
    public void recordResult(Match match) {
        if (match.getHomeGoals() == null || match.getAwayGoals() == null) {
            throw new IllegalArgumentException("Cannot record a result for a match with no final score");
        }

        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        double expectedHome = 1.0 / (1.0 + Math.pow(10, -((home.getEloRating() + home.getHomeAdvantage()) - away.getEloRating()) / 400.0));
        double actualHome = actualScore(match.getHomeGoals(), match.getAwayGoals());
        double actualAway = 1 - actualHome;
        double expectedAway = 1 - expectedHome;

        home.setEloRating(home.getEloRating() + K_FACTOR * (actualHome - expectedHome));
        away.setEloRating(away.getEloRating() + K_FACTOR * (actualAway - expectedAway));

        home.setGoalsForAvg(ema(home.getGoalsForAvg(), match.getHomeGoals()));
        home.setGoalsAgainstAvg(ema(home.getGoalsAgainstAvg(), match.getAwayGoals()));
        away.setGoalsForAvg(ema(away.getGoalsForAvg(), match.getAwayGoals()));
        away.setGoalsAgainstAvg(ema(away.getGoalsAgainstAvg(), match.getHomeGoals()));

        int homePointsEarned = pointsFor(actualHome);
        int awayPointsEarned = pointsFor(actualAway);
        home.setFormPoints((int) Math.round(clamp(home.getFormPoints() * FORM_DECAY + homePointsEarned, 0, 15)));
        away.setFormPoints((int) Math.round(clamp(away.getFormPoints() * FORM_DECAY + awayPointsEarned, 0, 15)));

        teamRepository.save(home);
        teamRepository.save(away);
    }

    private double actualScore(int homeGoals, int awayGoals) {
        if (homeGoals > awayGoals) return 1.0;
        if (homeGoals < awayGoals) return 0.0;
        return 0.5;
    }

    private int pointsFor(double actualScore) {
        if (actualScore == 1.0) return 3;
        if (actualScore == 0.5) return 1;
        return 0;
    }

    private double ema(double previous, double latest) {
        return previous + GOAL_AVG_ALPHA * (latest - previous);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
