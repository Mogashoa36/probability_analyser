package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamDto {
    private Long id;
    private String name;
    private String league;
    private double eloRating;
    private int formPoints;
    private double goalsForAvg;
    private double goalsAgainstAvg;
}
