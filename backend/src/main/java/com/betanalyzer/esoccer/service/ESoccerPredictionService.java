package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.dto.CreateMatchRequest;
import com.betanalyzer.esoccer.dto.MatchSummaryDto;
import com.betanalyzer.esoccer.dto.PredictionResponse;
import com.betanalyzer.esoccer.dto.PlayerFormResponse;
import com.betanalyzer.esoccer.dto.HeadToHeadResponse;
import com.betanalyzer.esoccer.entity.ESoccerGameMode;
import com.betanalyzer.esoccer.entity.ESoccerCompetition;
import com.betanalyzer.esoccer.entity.ESoccerMatch;
import com.betanalyzer.esoccer.entity.ESoccerOdds;
import com.betanalyzer.esoccer.entity.ESoccerPlayer;
import com.betanalyzer.esoccer.entity.ESoccerPlayerRating;
import com.betanalyzer.esoccer.entity.ESoccerPrediction;
import com.betanalyzer.esoccer.repository.ESoccerMatchRepository;
import com.betanalyzer.esoccer.repository.ESoccerOddsRepository;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRatingRepository;
import com.betanalyzer.esoccer.repository.ESoccerPredictionRepository;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRepository;
import com.betanalyzer.esoccer.repository.ESoccerTeamRepository;
import com.betanalyzer.esoccer.repository.ESoccerCompetitionRepository;
import com.betanalyzer.esoccer.repository.ESoccerGameModeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ESoccerPredictionService {

    private final ESoccerMatchRepository matchRepository;
    private final ESoccerPlayerRepository playerRepository;
    private final ESoccerTeamRepository teamRepository;
    private final ESoccerCompetitionRepository competitionRepository;
    private final ESoccerGameModeRepository gameModeRepository;
    private final ESoccerPlayerRatingRepository ratingRepository;
    private final ESoccerOddsRepository oddsRepository;
    private final ESoccerPredictionRepository predictionRepository;
    private final ESoccerStatisticsService statisticsService;
    private final ESoccerProbabilityCalculationService probabilityService;

    public ESoccerPredictionService(
            ESoccerMatchRepository matchRepository,
            ESoccerPlayerRepository playerRepository,
            ESoccerTeamRepository teamRepository,
            ESoccerCompetitionRepository competitionRepository,
            ESoccerGameModeRepository gameModeRepository,
            ESoccerPlayerRatingRepository ratingRepository,
            ESoccerOddsRepository oddsRepository,
            ESoccerPredictionRepository predictionRepository,
            ESoccerStatisticsService statisticsService,
            ESoccerProbabilityCalculationService probabilityService) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.competitionRepository = competitionRepository;
        this.gameModeRepository = gameModeRepository;
        this.ratingRepository = ratingRepository;
        this.oddsRepository = oddsRepository;
        this.predictionRepository = predictionRepository;
        this.statisticsService = statisticsService;
        this.probabilityService = probabilityService;
    }

    @Transactional(readOnly = true)
    public PredictionResponse predict(Long matchId) {
        ESoccerMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
        ESoccerPlayer playerA = match.getPlayerA();
        ESoccerPlayer playerB = match.getPlayerB();
        PlayerFormStats formA = statisticsService.getLastFiveMatches(playerA.getId());
        PlayerFormStats formB = statisticsService.getLastFiveMatches(playerB.getId());
        HeadToHeadStats h2h = statisticsService.getHeadToHead(playerA.getId(), playerB.getId());
        ESoccerPlayerRating ratingA = getOrCreateRating(playerA.getId(), match.getCompetition().getId());
        ESoccerPlayerRating ratingB = getOrCreateRating(playerB.getId(), match.getCompetition().getId());
        ESoccerGameMode gameMode = match.getGameMode();
        var outcome = probabilityService.computeOutcomeProbabilities(ratingA, ratingB, formA, formB, h2h, gameMode);
        var goals = probabilityService.computeGoalProbabilities(formA, formB, h2h, gameMode);
        var doubleChance = probabilityService.computeDoubleChance(outcome);
        var confidence = computeConfidence(formA, formB, h2h, ratingA, ratingB);
        String explanation = buildExplanation(formA, formB, h2h, outcome, goals, confidence);
        String supportingStats = buildSupportingStats(formA, formB, h2h, ratingA, ratingB);
        int sampleSize = Math.min(formA.matchesConsidered(), formB.matchesConsidered());
        if (h2h.encounters() > 0) sampleSize = Math.min(sampleSize, Math.max(h2h.encounters(), 1));
        sampleSize = Math.max(sampleSize, 1);
        var oddsDto = buildOddsComparison(match, outcome, goals, doubleChance);
        PredictionResponse response = new PredictionResponse(
                match.getId(),
                playerA.getGamerId(), playerB.getGamerId(),
                match.getTeamA().getName(), match.getTeamB().getName(),
                match.getCompetition().getName(), match.getGameMode().getName(),
                match.getMatchDateTime(), match.getDurationMinutes(),
                new PredictionResponse.OutcomeProbabilitiesDto(
                        outcome.probPlayerAWin(), outcome.probDraw(), outcome.probPlayerBWin(),
                        gameMode != null && gameMode.isAllowDraws()),
                new PredictionResponse.GoalMarketProbabilitiesDto(
                        goals.probOver1_5(), goals.probOver2_5(), goals.probOver3_5(),
                        goals.probUnder2_5(), goals.probBttsYes()),
                new PredictionResponse.DoubleChanceProbabilitiesDto(
                        doubleChance.prob1X(), doubleChance.probX2(), doubleChance.prob12(),
                        gameMode != null && gameMode.isAllowDraws()),
                new PredictionResponse.ConfidenceDto(
                        confidence.level(), confidence.score(), confidence.factors()),
                explanation, supportingStats, sampleSize, oddsDto);
        persistPrediction(match, response);
        return response;
    }    @Transactional(readOnly = true)
    public PlayerFormResponse getPlayerForm(Long playerId) {
        ESoccerPlayer player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        PlayerFormStats stats = statisticsService.getLastFiveMatches(playerId);
        return new PlayerFormResponse(stats.playerName(), stats.gamerId(), stats.matchesConsidered(),
                stats.wins(), stats.draws(), stats.losses(), stats.winPercentage(),
                stats.avgGoalsScored(), stats.avgGoalsConceded(), stats.avgTotalGoals(),
                stats.avgGoalDifference(), stats.over1_5Frequency(), stats.over2_5Frequency(),
                stats.bttsFrequency(), stats.losingStreak(), stats.highScoringGames(),
                stats.lastMatches().stream().map(m -> new PlayerFormResponse.MatchSummaryDto(m.opponent(), m.selectedTeam(), m.result(), m.goalsScored(), m.goalsConceded(), m.matchDate(), m.competition(), m.gameMode(), m.durationMinutes())).collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public HeadToHeadResponse getHeadToHead(Long playerAId, Long playerBId) {
        HeadToHeadStats h2h = statisticsService.getHeadToHead(playerAId, playerBId);
        return new HeadToHeadResponse(h2h.playerA(), h2h.playerB(), h2h.encounters(),
                h2h.playerAWins(), h2h.playerBWins(), h2h.draws(), h2h.avgGoalsPerEncounter(), h2h.mostFrequentScoreline(),
                h2h.highestScoringEncounter(), h2h.lowestScoringEncounter(), h2h.over1_5Percentage(), h2h.over2_5Percentage(), h2h.bttsPercentage(),
                h2h.consecutiveWinsAgainstOpponent(), h2h.sampleSizeWarning(),
                h2h.encountersList().stream().map(m -> new HeadToHeadResponse.EncounterDto(m.playerA(), m.playerB(), m.goalsA(), m.goalsB(), m.result(), m.matchDate(), m.competition())).collect(Collectors.toList()));
    }

    @Transactional
    public void recordMatchResult(Long matchId, Integer goalsA, Integer goalsB) {
        ESoccerMatch match = matchRepository.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
        match.setGoalsA(goalsA); match.setGoalsB(goalsB); match.setStatus(ESoccerMatch.MatchStatus.PLAYED);
        matchRepository.save(match);
        updatePlayerRatings(match, goalsA, goalsB);
        predictionRepository.findByMatchId(matchId).ifPresent(p -> { p.setActualResult(resultLabel(goalsA, goalsB)); predictionRepository.save(p); });
    }

    @Transactional(readOnly = true)
    public List<MatchSummaryDto> listUpcoming() {
        LocalDateTime now = LocalDateTime.now();
        return matchRepository.findByStatusAndMatchDateTimeAfter(ESoccerMatch.MatchStatus.SCHEDULED, now).stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSummaryDto> listAllMatches() {
        return matchRepository.findAll().stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    public ESoccerMatch createMatch(com.betanalyzer.esoccer.dto.CreateMatchRequest request) {
        ESoccerPlayer pA = resolveOrCreatePlayer(request.playerAGamerId(), null);
        ESoccerPlayer pB = resolveOrCreatePlayer(request.playerBGamerId(), null);
        ESoccerTeam tA = resolveOrCreateTeam(request.teamAName(), null);
        ESoccerTeam tB = resolveOrCreateTeam(request.teamBName(), null);
        ESoccerCompetition comp = resolveOrCreateCompetition(request.competitionName(), null, true);
        ESoccerGameMode mode = resolveOrCreateGameMode(request.gameModeName(), null, request.durationMinutes(), comp.isAllowDraws());
        if (request.externalId() != null && matchRepository.existsByExternalId(request.externalId())) {
            throw new IllegalArgumentException("Match with externalId " + request.externalId() + " already exists");
        }
        ESoccerMatch match = new ESoccerMatch(pA, pB, tA, tB, comp, mode, request.matchDateTime(), request.externalId());
        match.setDurationMinutes(request.durationMinutes());
        match.setSource(ESoccerMatch.DataSource.MANUAL);
        return matchRepository.save(match);
    }    private ESoccerPlayerRating getOrCreateRating(Long playerId, Long competitionId) {
        return ratingRepository.findByPlayerIdAndCompetitionId(playerId, competitionId).orElseGet(() -> {
            ESoccerPlayer player = playerRepository.findById(playerId).orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
            ESoccerCompetition comp = competitionRepository.findById(competitionId).orElseThrow(() -> new IllegalArgumentException("Competition not found: " + competitionId));
            return ratingRepository.save(new ESoccerPlayerRating(player, comp));
        });
    }

    private void updatePlayerRatings(ESoccerMatch match, int goalsA, int goalsB) {
        ESoccerPlayerRating rA = getOrCreateRating(match.getPlayerA().getId(), match.getCompetition().getId());
        ESoccerPlayerRating rB = getOrCreateRating(match.getPlayerB().getId(), match.getCompetition().getId());
        double expectedA = 1.0 / (1.0 + Math.pow(10, -(rA.getRating() - rB.getRating()) / 400.0));
        double actualA = goalsA > goalsB ? 1.0 : (goalsA == goalsB ? 0.5 : 0.0);
        double actualB = 1 - actualA;
        double k = 20.0;
        rA.setRating(rA.getRating() + k * (actualA - expectedA));
        rB.setRating(rB.getRating() + k * (actualB - (1 - expectedA)));
        rA.setFormPoints((int) Math.round(clamp01(rA.getFormPoints() * 0.8 + pointsFor(actualA), 0, 15)));
        rB.setFormPoints((int) Math.round(clamp01(rB.getFormPoints() * 0.8 + pointsFor(actualB), 0, 15)));
        rA.setGoalsForAvg(ema(rA.getGoalsForAvg(), goalsA));
        rA.setGoalsAgainstAvg(ema(rA.getGoalsAgainstAvg(), goalsB));
        rB.setGoalsForAvg(ema(rB.getGoalsForAvg(), goalsB));
        rB.setGoalsAgainstAvg(ema(rB.getGoalsAgainstAvg(), goalsA));
        rA.setMatchesPlayed(rA.getMatchesPlayed() + 1);
        rB.setMatchesPlayed(rB.getMatchesPlayed() + 1);
        rA.setLastUpdated(LocalDateTime.now());
        rB.setLastUpdated(LocalDateTime.now());
        ratingRepository.save(rA);
        ratingRepository.save(rB);
    }

    private double ema(double previous, double latest) {
        return previous + 0.25 * (latest - previous);
    }

    private int pointsFor(double score) {
        if (score == 1.0) return 3;
        if (score == 0.5) return 1;
        return 0;
    }

    private double clamp01(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String resultLabel(int ga, int gb) {
        if (ga > gb) return "PLAYER_A_WIN";
        if (ga < gb) return "PLAYER_B_WIN";
        return "DRAW";
    }    private Confidence computeConfidence(PlayerFormStats formA, PlayerFormStats formB,
                                         HeadToHeadStats h2h,
                                         ESoccerPlayerRating rA, ESoccerPlayerRating rB) {
        List<String> factors = new ArrayList<>(); double score = 100.0;
        int totalMatches = formA.matchesConsidered() + formB.matchesConsidered();
        if (totalMatches < 4) { score -= 30; factors.add("Limited recent match history (" + totalMatches + " matches total)"); }
        else if (totalMatches < 8) { score -= 15; factors.add("Moderate recent match history"); }
        else { factors.add("Good recent match history"); }
        if (h2h.encounters() == 0) { score -= 20; factors.add("No head-to-head history"); }
        else if (h2h.encounters() < 3) { score -= 10; factors.add("Small head-to-head sample (" + h2h.encounters() + " encounters)"); }
        else { factors.add("Head-to-head history available (" + h2h.encounters() + " encounters)"); }
        if (rA.getMatchesPlayed() < 5 || rB.getMatchesPlayed() < 5) { score -= 15; factors.add("Player ratings still maturing"); }
        if (formA.losingStreak() || formB.losingStreak()) { score -= 10; factors.add("Recent losing streak detected"); }
        score = Math.max(0, Math.min(100, score));
        String level = score >= 70 ? "HIGH" : (score >= 40 ? "MEDIUM" : "LOW");
        return new Confidence(level, score, factors);
    }

    private String buildExplanation(PlayerFormStats formA, PlayerFormStats formB,
                                    HeadToHeadStats h2h,
                                    ESoccerProbabilityCalculationService.OutcomeProbabilities outcome,
                                    ESoccerProbabilityCalculationService.GoalMarketProbabilities goals,
                                    Confidence confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s has a %.0f%% win rate over their last %d matches, averaging %.1f goals scored and %.1f conceded.%n",
                formA.playerName(), formA.winPercentage(), formA.matchesConsidered(), formA.avgGoalsScored(), formA.avgGoalsConceded()));
        sb.append(String.format("%s has a %.0f%% win rate over their last %d matches, averaging %.1f goals scored and %.1f conceded.%n",
                formB.playerName(), formB.winPercentage(), formB.matchesConsidered(), formB.avgGoalsScored(), formB.avgGoalsConceded()));
        if (h2h.encounters() > 0) {
            sb.append(String.format("Head-to-head: %s leads %d-%d (%d draws) over %d encounters, averaging %.1f goals per match.%n",
                    h2h.playerAWins() >= h2h.playerBWins() ? h2h.playerA() : h2h.playerB(),
                    h2h.playerAWins(), h2h.playerBWins(), h2h.draws(), h2h.encounters(),
                    h2h.avgGoalsPerEncounter()));
        } else { sb.append("No head-to-head history available between these players.%n"); }
        sb.append(String.format("Model gives %.0f%% chance of %s win, %.0f%% draw, %.0f%% chance of %s win.%n",
                outcome.probPlayerAWin() * 100, formA.playerName(), outcome.probDraw() * 100, outcome.probPlayerBWin() * 100, formB.playerName()));
        sb.append(String.format("Goals: Over 2.5 at %.0f%%, BTTS at %.0f%%.%n",
                goals.probOver2_5() * 100, goals.probBttsYes() * 100))
        sb.append(String.format("Confidence: %s (%.0f%%). %s%n",
                confidence.level(), confidence.score(), String.join("; ", confidence.factors())));
        return sb.toString();
    }

    private String buildSupportingStats(PlayerFormStats formA, PlayerFormStats formB,
                                        HeadToHeadStats h2h,
                                        ESoccerPlayerRating rA, ESoccerPlayerRating rB) {
        return String.format(
                "{\"playerA\":{\"name\":\"%s\",\"rating\":%.0f,\"formPoints\":%d,\"avgGF\":%.2f,\"avgGA\":%.2f},\"playerB\":{\"name\":\"%s\",\"rating\":%.0f,\"formPoints\":%d,\"avgGF\":%.2f,\"avgGA\":%.2f},\"h2h\":{\"encounters\":%d,\"aWins\":%d,\"bWins\":%d,\"draws\":%d}}",
                formA.playerName(), rA.getRating(), rA.getFormPoints(), rA.getGoalsForAvg(), rA.getGoalsAgainstAvg(),
                formB.playerName(), rB.getRating(), rB.getFormPoints(), rB.getGoalsForAvg(), rB.getGoalsAgainstAvg(),
                h2h.encounters(), h2h.playerAWins(), h2h.playerBWins(), h2h.draws());
    }

    private record Confidence(String level, double score, List<String> factors) {}
}

