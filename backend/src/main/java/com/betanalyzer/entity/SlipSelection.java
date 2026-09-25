package com.betanalyzer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One leg ("selection") of a betting slip - e.g. "Chiefs vs Pirates - Home Win @ 2.10".
 * Selections are parsed from free-text or structured input and enriched
 * with a modelled probability before being persisted against a slip.
 */
@Entity
@Table(name = "slip_selection")
@Getter
@Setter
@NoArgsConstructor
public class SlipSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "slip_id")
    private BettingSlip slip;

    private String homeTeamName;
    private String awayTeamName;

    @Enumerated(EnumType.STRING)
    private Market market;

    /** Bookmaker odds as quoted (decimal odds, e.g. 1.85). */
    private double odds;

    /** Model's estimated probability of this selection winning (0-1). */
    private double modelProbability;

    /** Implied probability derived from the odds (1/odds), for comparison. */
    private double impliedProbability;

    /** modelProbability - impliedProbability. Positive = value bet. */
    private double edge;

    public enum Market {
        HOME_WIN, AWAY_WIN, DRAW, OVER_2_5, UNDER_2_5, BTTS_YES, BTTS_NO, UNKNOWN
    }
}
