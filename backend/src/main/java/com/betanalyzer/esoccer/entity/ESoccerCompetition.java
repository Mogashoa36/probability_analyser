package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "esoccer_competition", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name"),
        @UniqueConstraint(columnNames = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerCompetition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "external_id")
    private String externalId;
    @Column(name = "allow_draws", nullable = false)
    private boolean allowDraws = true;
    @Column(name = "default_duration_minutes")
    private Integer defaultDurationMinutes;
    public ESoccerCompetition(String name, String externalId, boolean allowDraws) {
        this.name = name;
        this.externalId = externalId;
        this.allowDraws = allowDraws;
    }
}
