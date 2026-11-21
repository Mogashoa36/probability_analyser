package com.betanalyzer.esoccer.service;

public record FormComparison(
        String betterFormPlayer,
        String moreConsistentScorer,
        String higherConceder,
        String betterGoalDifference,
        boolean playerALosingStreak,
        boolean playerBLosingStreak,
        boolean playerAHighScoring,
        boolean playerBHighScoring,
        double playerAFormScore, // 0-1 recency weighted
        double playerBFormScore
) {}
