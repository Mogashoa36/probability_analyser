package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "esoccer_game_mode", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name"),
        @UniqueConstraint(columnNames = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerGameMode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "external_id")
    private String externalId;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(name = "allow_draws", nullable = false)
    private boolean allowDraws = true;
    @Column(name = "avg_total_goals")
    private double avgTotalGoals = 2.5;
    public ESoccerGameMode(String name, String externalId, Integer durationMinutes, boolean allowDraws) {
        this.name = name;
        this.externalId = externalId;
        this.durationMinutes = durationMinutes;
        this.allowDraws = allowDraws;
    }
}
