package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "esoccer_match", indexes = {
        @Index(name = "idx_esoccer_match_player_a", columnList = "player_a_id"),
        @Index(name = "idx_esoccer_match_player_b", columnList = "player_b_id"),
        @Index(name = "idx_esoccer_match_players", columnList = "player_a_id, player_b_id"),
        @Index(name = "idx_esoccer_match_date", columnList = "match_date_time"),
        @Index(name = "idx_esoccer_match_competition", columnList = "competition_id"),
        @Index(name = "idx_esoccer_match_status", columnList = "status"),
        @Index(name = "idx_esoccer_match_external", columnList = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_a_id")
    private ESoccerPlayer playerA;
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_b_id")
    private ESoccerPlayer playerB;
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_a_id")
    private ESoccerTeam teamA;
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_b_id")
    private ESoccerTeam teamB;
    @ManyToOne(optional = false)
    @JoinColumn(name = "competition_id")
    private ESoccerCompetition competition;
    @ManyToOne(optional = false)
    @JoinColumn(name = "game_mode_id")
    private ESoccerGameMode gameMode;
    private LocalDateTime matchDateTime;
    private Integer durationMinutes;
    private Integer goalsA;
    private Integer goalsB;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.SCHEDULED;
    @Column(name = "external_id")
    private String externalId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataSource source = DataSource.MANUAL;

    public enum MatchStatus { SCHEDULED, PLAYED, POSTPONED }
    public enum DataSource { MANUAL, CSV, JSON, PROVIDER }

    public boolean isPlayed() {
        return status == MatchStatus.PLAYED && goalsA != null && goalsB != null;
    }

    public ESoccerMatch(ESoccerPlayer playerA, ESoccerPlayer playerB,
                        ESoccerTeam teamA, ESoccerTeam teamB,
                        ESoccerCompetition competition, ESoccerGameMode gameMode,
                        LocalDateTime matchDateTime, String externalId) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.teamA = teamA;
        this.teamB = teamB;
        this.competition = competition;
        this.gameMode = gameMode;
        this.matchDateTime = matchDateTime;
        this.externalId = externalId;
    }
}
