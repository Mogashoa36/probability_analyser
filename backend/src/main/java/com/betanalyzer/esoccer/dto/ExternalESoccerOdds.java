package com.betanalyzer.esoccer.dto;

public record ExternalESoccerOdds(
        String externalEventId,
        String market,
        String outcome,
        double decimalOdds,
        String bookmaker
) {}
