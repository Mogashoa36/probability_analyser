package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SlipAnalysisResponseDto {
    private List<SelectionResultDto> selections;
    private double totalOdds;
    private double combinedWinProbabilityPct;
    private double impliedProbabilityPct;
    private String overallRiskRating;      // LOW / MEDIUM / HIGH / VERY HIGH
    private List<String> refinedSuggestions;
    private List<SelectionResultDto> weakestLegs;
}
