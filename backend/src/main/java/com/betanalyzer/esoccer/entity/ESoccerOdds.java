package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "esoccer_odds", indexes = {
        @Index(name = "idx_esoccer_odds_match", columnList = "match_id"),
        @Index(name = "idx_esoccer_odds_external", columnList = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerOdds {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private ESoccerMatch match;
    @Column(nullable = false)
    private String market;
    @Column(nullable = false)
    private String outcome;
    @Column(nullable = false)
    private double decimalOdds;
    @Column(nullable = false)
    private String bookmaker = "Betway";
    private LocalDateTime oddsDate = LocalDateTime.now();
    @Column(name = "external_id")
    private String externalId;
    public ESoccerOdds(ESoccerMatch match, String market, String outcome, double decimalOdds, String bookmaker) {
        this.match = match;
        this.market = market;
        this.outcome = outcome;
        this.decimalOdds = decimalOdds;
        this.bookmaker = bookmaker;
    }
    public double impliedProbability() {
        if (decimalOdds <= 0) return 0;
        return 1.0 / decimalOdds;
    }
}
