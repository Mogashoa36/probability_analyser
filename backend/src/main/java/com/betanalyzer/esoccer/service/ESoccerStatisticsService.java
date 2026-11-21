package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.entity.ESoccerMatch;
import com.betanalyzer.esoccer.repository.ESoccerMatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes historical statistics for eSoccer predictions:
 * last-5 match form, head-to-head records, and form comparison between players.
 * All calculations use only completed matches.
 */
@Slf4j
@Service
public class ESoccerStatisticsService {

    private static final int RECENT_MATCHES = 5;
    private static final int FORM_MATCHES_3 = 3;

    private final ESoccerMatchRepository matchRepository;

    public ESoccerStatisticsService(ESoccerMatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    /** Last 5 completed matches for a player, with full aggregates. */
    @Transactional(readOnly = true)
    public PlayerFormStats getLastFiveMatches(Long playerId) {
        List<ESoccerMatch> completed = matchRepository.findCompletedByPlayer(playerId);
        List<ESoccerMatch> lastFive = completed.stream()
                .limit(RECENT_MATCHES)
                .collect(Collectors.toList());

        if (lastFive.isEmpty()) {
            return emptyFormStats("Unknown", playerId);
        }

        ESoccerMatch first = lastFive.get(0);
        String playerName = (first.getPlayerA().getId().equals(playerId))
                ? first.getPlayerA().getGamerId() : first.getPlayerB().getGamerId();

        int wins = 0, draws = 0, losses = 0;
        int goalsFor = 0, goalsAgainst = 0;
        int over1_5 = 0, over2_5 = 0, btts = 0;
        boolean losingStreak = true;
        boolean highScoring = false;
        List<PlayerFormStats.MatchSummary> summaries = new ArrayList<>();

        for (ESoccerMatch m : lastFive) {
            boolean isA = m.getPlayerA().getId().equals(playerId);
            int gf = isA ? m.getGoalsA() : m.getGoalsB();
            int ga = isA ? m.getGoalsB() : m.getGoalsA();
            String opponent = isA ? m.getPlayerB().getGamerId() : m.getPlayerA().getGamerId();

            String result;
            if (gf > ga) { result = "W"; wins++; }
            else if (gf == ga) { result = "D"; draws++; }
            else { result = "L"; losses++; }

            goalsFor += gf;
            goalsAgainst += ga;
            int total = gf + ga;
            if (total > 1.5) over1_5++;
            if (total > 2.5) over2_5++;
            if (gf > 0 && ga > 0) btts++;
            if (total >= 3) highScoring = true;

            summaries.add(new PlayerFormStats.MatchSummary(
                    opponent,
                    isA ? m.getTeamA().getName() : m.getTeamB().getName(),
                    result, gf, ga,
                    m.getMatchDateTime() != null ? m.getMatchDateTime().toString() : "",
                    m.getCompetition().getName(),
                    m.getGameMode().getName(),
                    m.getDurationMinutes()
            ));
        }

        // Losing streak: check from most recent backwards
        losingStreak = false;
        int streak = 0;
        for (int i = summaries.size() - 1; i >= 0; i--) {
            if ("L".equals(summaries.get(i).result())) streak++;
            else break;
        }
        losingStreak = streak >= 2;

        double n = lastFive.size();
        double winPct = n > 0 ? wins / n * 100 : 0;
        double avgGf = n > 0 ? (double) goalsFor / n : 0;
        double avgGa = n > 0 ? (double) goalsAgainst / n : 0;
        double avgTotal = n > 0 ? (double) (goalsFor + goalsAgainst) / n : 0;
        double avgGd = n > 0 ? (double) (goalsFor - goalsAgainst) / n : 0;
        double o15 = n > 0 ? over1_5 / n * 100 : 0;
        double o25 = n > 0 ? over2_5 / n * 100 : 0;
        double bttsPct = n > 0 ? btts / n * 100 : 0;

        return new PlayerFormStats(
                playerName,
                first.getPlayerA().getId().equals(playerId) ? first.getPlayerA().getGamerId() : first.getPlayerB().getGamerId(),
                lastFive.size(),
                wins, draws, losses,
                round(winPct, 1),
                round(avgGf, 2),
                round(avgGa, 2),
                round(avgTotal, 2),
                round(avgGd, 2),
                round(o15, 1),
                round(o25, 1),
                round(bttsPct, 1),
                summaries,
                losingStreak,
                highScoring
        );
    }

    /** Head-to-head statistics between two players, up to 5 most recent encounters. */
    @Transactional(readOnly = true)
    public HeadToHeadStats getHeadToHead(Long playerAId, Long playerBId) {
        List<ESoccerMatch> h2h = matchRepository.findHeadToHead(playerAId, playerBId);
        List<ESoccerMatch> recent = h2h.stream().limit(5).collect(Collectors.toList());

        ESoccerMatch first = recent.isEmpty() ? (h2h.isEmpty() ? null : h2h.get(0)) : recent.get(0);
        if (first == null) {
            return emptyH2H("Player A", "Player B");
        }

        String nameA = first.getPlayerA().getGamerId();
        String nameB = first.getPlayerB().getGamerId();

        int aWins = 0, bWins = 0, draws = 0;
        int totalGoals = 0;
        Map<String, Integer> scorelines = new HashMap<>();
        int highest = 0, lowest = Integer.MAX_VALUE;
        int over1_5 = 0, over2_5 = 0, btts = 0;
        int consecutiveA = 0, consecutiveB = 0;
        int currentStreakA = 0, currentStreakB = 0;

        List<HeadToHeadStats.MatchSummary> summaries = new ArrayList<>();

        for (ESoccerMatch m : recent) {
            int ga = m.getGoalsA(), gb = m.getGoalsB();
            int total = ga + gb;
            totalGoals += total;

            String score = ga + "-" + gb;
            scorelines.merge(score, 1, Integer::sum);

            if (total > highest) highest = total;
            if (total < lowest) lowest = total;
            if (total > 1.5) over1_5++;
            if (total > 2.5) over2_5++;
            if (ga > 0 && gb > 0) btts++;

            String result;
            if (ga > gb) { result = "A_WIN"; aWins++; currentStreakA++; currentStreakB = 0; }
            else if (ga == gb) { result = "DRAW"; draws++; currentStreakA = 0; currentStreakB = 0; }
            else { result = "B_WIN"; bWins++; currentStreakB++; currentStreakA = 0; }

            summaries.add(new HeadToHeadStats.MatchSummary(
                    nameA, nameB, ga, gb, result,
                    m.getMatchDateTime() != null ? m.getMatchDateTime().toString() : "",
                    m.getCompetition().getName()
            ));
        }

        int n = recent.size();
        double avgGoals = n > 0 ? (double) totalGoals / n : 0;
        String mostFreq = scorelines.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        double o15pct = n > 0 ? over1_5 / (double) n * 100 : 0;
        double o25pct = n > 0 ? over2_5 / (double) n * 100 : 0;
        double bttsPct = n > 0 ? btts / (double) n * 100 : 0;

        int consecutiveWinsAgainstOpponent = currentStreakA > 0 ? currentStreakA : -currentStreakB;

        return new HeadToHeadStats(
                nameA, nameB,
                n, aWins, bWins, draws,
                round(avgGoals, 2),
                mostFreq,
                highest > 0 ? highest : 0,
                lowest != Integer.MAX_VALUE ? lowest : 0,
                round(o15pct, 1),
                round(o25pct, 1),
                round(bttsPct, 1),
                consecutiveWinsAgainstOpponent,
                summaries,
                n < 5
        );
    }

    /** Compare recent form between two players. */
    @Transactional(readOnly = true)
    public FormComparison compareForm(Long playerAId, Long playerBId) {
        PlayerFormStats a = getLastFiveMatches(playerAId);
        PlayerFormStats b = getLastFiveMatches(playerBId);

        String betterForm = a.winPercentage() >= b.winPercentage() ? a.playerName() : b.playerName();
        String consistentScorer = a.avgGoalsScored() >= b.avgGoalsScored() ? a.playerName() : b.playerName();
        String higherConceder = a.avgGoalsConceded() >= b.avgGoalsConceded() ? a.playerName() : b.playerName();
        String betterGd = a.avgGoalDifference() >= b.avgGoalDifference() ? a.playerName() : b.playerName();

        double aFormScore = recencyFormScore(a);
        double bFormScore = recencyFormScore(b);

        return new FormComparison(
                betterForm,
                consistentScorer,
                higherConceder,
                betterGd,
                a.losingStreak(),
                b.losingStreak(),
                a.highScoringGames(),
                b.highScoringGames(),
                round(aFormScore, 2),
                round(bFormScore, 2)
        );
    }

    /** Recency-weighted form score: weights recent matches more heavily. */
    private double recencyFormScore(PlayerFormStats stats) {
        if (stats.matchesConsidered() == 0) return 0.0;
        double totalWeight = 0;
        double weightedPoints = 0;
        List<PlayerFormStats.MatchSummary> matches = stats.lastMatches();
        // Most recent match is last in the list (repository returns desc, but we collected in order)
        Collections.reverse(matches);
        for (int i = 0; i < matches.size(); i++) {
            double weight = Math.pow(0.7, matches.size() - 1 - i); // newer = higher weight
            String r = matches.get(i).result();
            double points = switch (r) {
                case "W" -> 3.0;
                case "D" -> 1.0;
                default -> 0.0;
            };
            weightedPoints += points * weight;
            totalWeight += weight;
        }
        return totalWeight > 0 ? weightedPoints / totalWeight * 100 / 3.0 : 0.0; // 0-100
    }

    private PlayerFormStats emptyFormStats(String name, Long id) {
        return new PlayerFormStats(name, id != null ? id.toString() : "", 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), false, false);
    }

    private HeadToHeadStats emptyH2H(String a, String b) {
        return new HeadToHeadStats(a, b, 0, 0, 0, 0, 0, "N/A", 0, 0, 0, 0, 0, 0, List.of(), true);
    }

    private static double round(double v, int places) {
        double scale = Math.pow(10, places);
        return Math.round(v * scale) / scale;
    }
}
