package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SelectionResultDto {
    private String homeTeam;
    private String awayTeam;
    private String market;
    private double odds;
    private double modelProbabilityPct;   // 0-100, rounded
    private double impliedProbabilityPct; // 0-100, rounded
    private double edgePct;               // model - implied
    private String confidence;            // LOW / MEDIUM / HIGH
    private String note;                  // human-readable reasoning
}
