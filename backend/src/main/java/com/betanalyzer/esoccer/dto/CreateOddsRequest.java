package com.betanalyzer.esoccer.dto;

public record CreateOddsRequest(
        Long matchId, String market, String outcome,
        double decimalOdds, String bookmaker
) {}
