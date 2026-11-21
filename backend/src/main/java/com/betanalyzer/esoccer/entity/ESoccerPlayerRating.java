package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "esoccer_player_rating", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"player_id", "competition_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerPlayerRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    private ESoccerPlayer player;
    @ManyToOne
    @JoinColumn(name = "competition_id")
    private ESoccerCompetition competition;
    @Column(nullable = false)
    private double rating = 1500.0;
    @Column(nullable = false)
    private int matchesPlayed = 0;
    @Column(name = "form_points", nullable = false)
    private int formPoints = 7;
    @Column(name = "goals_for_avg")
    private double goalsForAvg = 1.2;
    @Column(name = "goals_against_avg")
    private double goalsAgainstAvg = 1.2;
    private LocalDateTime lastUpdated = LocalDateTime.now();
    public ESoccerPlayerRating(ESoccerPlayer player, ESoccerCompetition competition) {
        this.player = player;
        this.competition = competition;
    }
}
