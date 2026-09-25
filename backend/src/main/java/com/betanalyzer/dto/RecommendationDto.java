package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RecommendationDto {
    private Long matchId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String recommendedMarket;
    private double modelProbabilityPct;
    private double confidenceScore;   // 0-100 composite score used for ranking
    private String reasoning;
}
