package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchDto {
    private Long id;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private LocalDateTime kickOff;
    private String status;
    private Integer homeGoals;
    private Integer awayGoals;
}
