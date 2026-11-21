package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "esoccer_prediction", indexes = {
        @Index(name = "idx_esoccer_prediction_match", columnList = "match_id"),
        @Index(name = "idx_esoccer_prediction_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private ESoccerMatch match;
    private Double probPlayerAWin;
    private Double probDraw;
    private Double probPlayerBWin;
    private Double probOver1_5;
    private Double probOver2_5;
    private Double probOver3_5;
    private Double probUnder2_5;
    private Double probBttsYes;
    private Double probDoubleChance1X;
    private Double probDoubleChanceX2;
    private Double probDoubleChance12;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfidenceLevel confidence = ConfidenceLevel.LOW;
    @Column(length = 4000)
    private String explanation;
    @Column(length = 4000)
    private String supportingStatistics;
    private int sampleSize;
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "actual_result")
    private String actualResult;

    public enum ConfidenceLevel { HIGH, MEDIUM, LOW }

    public ESoccerPrediction(ESoccerMatch match) {
        this.match = match;
    }
}
