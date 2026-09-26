package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class RecommendationDto {
    private Long matchId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    /** Kick-off date/time of the fixture, null when it came from a pasted
     *  list (a pasted fixture has no kick-off time attached to it). */
    private LocalDateTime kickOff;
    private String recommendedMarket;
    private double modelProbabilityPct;
    private double confidenceScore;   // 0-100 composite score used for ranking
    private String reasoning;
}
