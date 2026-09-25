package com.betanalyzer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A team's rolling strength profile, used as the core input to
 * probability calculations. Ratings are meant to be updated after
 * every completed match via TeamStrengthService.
 */
@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String league;

    /** Elo-style strength rating. League average starts at 1500. */
    @Column(name = "elo_rating", nullable = false)
    private double eloRating = 1500;

    /** Points earned in the last 5 matches (0-15). Short-term form signal. */
    @Column(name = "form_points")
    private int formPoints = 0;

    @Column(name = "goals_for_avg")
    private double goalsForAvg = 1.2;

    @Column(name = "goals_against_avg")
    private double goalsAgainstAvg = 1.2;

    /** Extra Elo points added when this team plays at home. */
    @Column(name = "home_advantage")
    private double homeAdvantage = 55;

    public Team(String name, String league, double eloRating) {
        this.name = name;
        this.league = league;
        this.eloRating = eloRating;
    }
}
