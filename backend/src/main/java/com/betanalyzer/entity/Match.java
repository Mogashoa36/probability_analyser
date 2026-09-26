package com.betanalyzer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A fixture between two teams. Used both for historical results
 * (feeding the strength model) and upcoming fixtures (feeding the
 * recommendation engine).
 */
@Entity
@Table(name = "match_fixture")
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    private String league;

    /**
     * Identifier for this fixture at the upstream data provider, namespaced as
     * "sofascore:98765". Null for manually created fixtures. Lets a re-sync
     * update the same row, and - importantly - lets the importer recognise a
     * result it has already applied to the strength model, so Elo is never
     * updated twice for one match.
     */
    @Column(name = "external_id", unique = true)
    private String externalId;

    private LocalDateTime kickOff;

    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.SCHEDULED;

    /** Null until the match has been played. */
    private Integer homeGoals;
    private Integer awayGoals;

    public enum MatchStatus {
        SCHEDULED, PLAYED, POSTPONED
    }
}
