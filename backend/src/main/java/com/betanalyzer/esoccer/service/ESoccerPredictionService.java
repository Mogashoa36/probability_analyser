
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
import com.betanalyzer.esoccer.entity.ESoccerTeam;

import com.betanalyzer.esoccer.repository.ESoccerMatchRepository;
import com.betanalyzer.esoccer.repository.ESoccerOddsRepository;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRatingRepository;
import com.betanalyzer.esoccer.repository.ESoccerPredictionRepository;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRepository;
import com.betanalyzer.esoccer.repository.ESoccerTeamRepository;
import com.betanalyzer.esoccer.repository.ESoccerCompetitionRepository;
import com.betanalyzer.esoccer.repository.ESoccerGameModeRepository;

import com.betanalyzer.esoccer.service.ESoccerProbabilityCalculationService.DoubleChanceProbabilities;
import com.betanalyzer.esoccer.service.ESoccerProbabilityCalculationService.GoalMarketProbabilities;
import com.betanalyzer.esoccer.service.ESoccerProbabilityCalculationService.OutcomeProbabilities;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    // =========================================================
    // PREDICTION
    // =========================================================

    @Transactional
    public PredictionResponse predict(Long matchId) {

        ESoccerMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        ESoccerPlayer playerA = match.getPlayerA();
        ESoccerPlayer playerB = match.getPlayerB();

        PlayerFormStats formA = statisticsService.getLastFiveMatches(playerA.getId());

        PlayerFormStats formB = statisticsService.getLastFiveMatches(playerB.getId());

        HeadToHeadStats h2h = statisticsService.getHeadToHead(playerA.getId(), playerB.getId());

        ESoccerPlayerRating ratingA = getOrCreateRating(
                playerA.getId(),
                match.getCompetition().getId());

        ESoccerPlayerRating ratingB = getOrCreateRating(
                playerB.getId(),
                match.getCompetition().getId());

        ESoccerGameMode gameMode = match.getGameMode();

        OutcomeProbabilities outcome = probabilityService.computeOutcomeProbabilities(
                ratingA, ratingB, formA, formB, h2h, gameMode);

        GoalMarketProbabilities goals = probabilityService.computeGoalProbabilities(
                formA, formB, h2h, gameMode);

        DoubleChanceProbabilities doubleChance = probabilityService.computeDoubleChance(outcome);

        PredictionResponse.ConfidenceDto confidence = computeConfidence(formA, formB, h2h, ratingA, ratingB);

        String explanation = buildExplanation(formA, formB, h2h, outcome, goals, confidence);

        String supportingStats = buildSupportingStats(formA, formB, h2h, ratingA, ratingB);

        int sampleSize = Math.min(
                formA.matchesConsidered(),
                formB.matchesConsidered());

        if (h2h.encounters() > 0) {
            sampleSize = Math.min(sampleSize, h2h.encounters());
        }

        sampleSize = Math.max(sampleSize, 1);

        PredictionResponse.OddsComparisonDto oddsDto = buildOddsComparison(match, outcome, goals, doubleChance);

        PredictionResponse response = new PredictionResponse(
                match.getId(),
                playerA.getGamerId(),
                playerB.getGamerId(),
                match.getTeamA().getName(),
                match.getTeamB().getName(),
                match.getCompetition().getName(),
                gameMode.getName(),
                match.getMatchDateTime(),
                match.getDurationMinutes(),

                new PredictionResponse.OutcomeProbabilitiesDto(
                        outcome.probPlayerAWin(),
                        outcome.probDraw(),
                        outcome.probPlayerBWin(),
                        gameMode.isAllowDraws()),

                new PredictionResponse.GoalMarketProbabilitiesDto(
                        goals.probOver1_5(),
                        goals.probOver2_5(),
                        goals.probOver3_5(),
                        goals.probUnder2_5(),
                        goals.probBttsYes()),

                new PredictionResponse.DoubleChanceProbabilitiesDto(
                        doubleChance.prob1X(),
                        doubleChance.probX2(),
                        doubleChance.prob12(),
                        gameMode.isAllowDraws()),

                confidence,
                explanation,
                supportingStats,
                sampleSize,
                oddsDto);

        persistPrediction(match, response);

        return response;
    }

    // =========================================================
    // PLAYER FORM
    // =========================================================

    @Transactional(readOnly = true)
    public PlayerFormResponse getPlayerForm(Long playerId) {

        playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        PlayerFormStats stats = statisticsService.getLastFiveMatches(playerId);

        return new PlayerFormResponse(
                stats.playerName(),
                stats.gamerId(),
                stats.matchesConsidered(),
                stats.wins(),
                stats.draws(),
                stats.losses(),
                stats.winPercentage(),
                stats.avgGoalsScored(),
                stats.avgGoalsConceded(),
                stats.avgTotalGoals(),
                stats.avgGoalDifference(),
                stats.over1_5Frequency(),
                stats.over2_5Frequency(),
                stats.bttsFrequency(),
                stats.losingStreak(),
                stats.highScoringGames(),

                stats.lastMatches().stream()
                        .map(m -> new PlayerFormResponse.MatchSummaryDto(
                                m.opponent(),
                                m.selectedTeam(),
                                m.result(),
                                m.goalsScored(),
                                m.goalsConceded(),
                                m.matchDate(),
                                m.competition(),
                                m.gameMode(),
                                m.durationMinutes()))
                        .collect(Collectors.toList()));
    }

    // =========================================================
    // HEAD TO HEAD
    // =========================================================

    @Transactional(readOnly = true)
    public HeadToHeadResponse getHeadToHead(
            Long playerAId,
            Long playerBId) {

        HeadToHeadStats h2h = statisticsService.getHeadToHead(playerAId, playerBId);

        return new HeadToHeadResponse(
                h2h.playerA(),
                h2h.playerB(),
                h2h.encounters(),
                h2h.playerAWins(),
                h2h.playerBWins(),
                h2h.draws(),
                h2h.avgGoalsPerEncounter(),
                h2h.mostFrequentScoreline(),
                h2h.highestScoringEncounter(),
                h2h.lowestScoringEncounter(),
                h2h.over1_5Percentage(),
                h2h.over2_5Percentage(),
                h2h.bttsPercentage(),
                h2h.consecutiveWinsAgainstOpponent(),
                h2h.sampleSizeWarning(),

                h2h.encountersList().stream()
                        .map(m -> new HeadToHeadResponse.EncounterDto(
                                m.playerA(),
                                m.playerB(),
                                m.goalsA(),
                                m.goalsB(),
                                m.result(),
                                m.matchDate(),
                                m.competition()))
                        .collect(Collectors.toList()));
    }

    // =========================================================
    // RECORD MATCH RESULT
    // =========================================================

    @Transactional
    public void recordMatchResult(
            Long matchId,
            Integer goalsA,
            Integer goalsB) {

        if (goalsA == null || goalsB == null || goalsA < 0 || goalsB < 0) {
            throw new IllegalArgumentException(
                    "Match goals must be valid non-negative numbers");
        }

        ESoccerMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        match.setGoalsA(goalsA);
        match.setGoalsB(goalsB);
        match.setStatus(ESoccerMatch.MatchStatus.PLAYED);

        matchRepository.save(match);

        updatePlayerRatings(match, goalsA, goalsB);

        predictionRepository.findByMatchId(matchId).ifPresent(prediction -> {
            prediction.setActualResult(resultLabel(goalsA, goalsB));
            predictionRepository.save(prediction);
        });
    }

    // =========================================================
    // MATCH LISTING
    // =========================================================

    @Transactional(readOnly = true)
    public List<MatchSummaryDto> listUpcoming() {

        LocalDateTime now = LocalDateTime.now();

        return matchRepository
                .findByStatusAndMatchDateTimeAfter(
                        ESoccerMatch.MatchStatus.SCHEDULED, now)
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSummaryDto> listAllMatches() {

        return matchRepository.findAll()
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    // =========================================================
    // CREATE MATCH
    // =========================================================

    @Transactional
    public ESoccerMatch createMatch(CreateMatchRequest request) {

        ESoccerPlayer playerA = resolveOrCreatePlayer(request.playerAGamerId(), null);

        ESoccerPlayer playerB = resolveOrCreatePlayer(request.playerBGamerId(), null);

        ESoccerTeam teamA = resolveOrCreateTeam(request.teamAName(), null);

        ESoccerTeam teamB = resolveOrCreateTeam(request.teamBName(), null);

        ESoccerCompetition competition = resolveOrCreateCompetition(
                request.competitionName(), null, true);

        ESoccerGameMode gameMode = resolveOrCreateGameMode(
                request.gameModeName(),
                null,
                request.durationMinutes(),
                competition.isAllowDraws());

        if (request.externalId() != null
                && matchRepository.existsByExternalId(request.externalId())) {

            throw new IllegalArgumentException(
                    "Match with externalId " + request.externalId() + " already exists");
        }

        ESoccerMatch match = new ESoccerMatch(
                playerA,
                playerB,
                teamA,
                teamB,
                competition,
                gameMode,
                request.matchDateTime(),
                request.externalId());

        match.setDurationMinutes(request.durationMinutes());
        match.setSource(ESoccerMatch.DataSource.MANUAL);

        return matchRepository.save(match);
    }

    // =========================================================
    // PLAYER RATINGS
    // =========================================================

    private ESoccerPlayerRating getOrCreateRating(
            Long playerId,
            Long competitionId) {

        return ratingRepository
                .findByPlayerIdAndCompetitionId(playerId, competitionId)
                .orElseGet(() -> {

                    ESoccerPlayer player = playerRepository.findById(playerId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Player not found: " + playerId));

                    ESoccerCompetition competition = competitionRepository.findById(competitionId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Competition not found: " + competitionId));

                    return ratingRepository.save(
                            new ESoccerPlayerRating(player, competition));
                });
    }

    private void updatePlayerRatings(
            ESoccerMatch match,
            int goalsA,
            int goalsB) {

        ESoccerPlayerRating ratingA = getOrCreateRating(
                match.getPlayerA().getId(),
                match.getCompetition().getId());

        ESoccerPlayerRating ratingB = getOrCreateRating(
                match.getPlayerB().getId(),
                match.getCompetition().getId());

        double expectedA = 1.0 / (1.0 + Math.pow(
                10,
                -(ratingA.getRating() - ratingB.getRating()) / 400.0));

        double actualA = goalsA > goalsB
                ? 1.0
                : (goalsA == goalsB ? 0.5 : 0.0);

        double actualB = 1.0 - actualA;
        double k = 20.0;

        ratingA.setRating(
                ratingA.getRating() + k * (actualA - expectedA));

        ratingB.setRating(
                ratingB.getRating() + k * (actualB - (1.0 - expectedA)));

        ratingA.setFormPoints(
                (int) Math.round(clamp(
                        ratingA.getFormPoints() * 0.8 + pointsFor(actualA),
                        0,
                        15)));

        ratingB.setFormPoints(
                (int) Math.round(clamp(
                        ratingB.getFormPoints() * 0.8 + pointsFor(actualB),
                        0,
                        15)));

        ratingA.setGoalsForAvg(
                ema(ratingA.getGoalsForAvg(), goalsA));

        ratingA.setGoalsAgainstAvg(
                ema(ratingA.getGoalsAgainstAvg(), goalsB));

        ratingB.setGoalsForAvg(
                ema(ratingB.getGoalsForAvg(), goalsB));

        ratingB.setGoalsAgainstAvg(
                ema(ratingB.getGoalsAgainstAvg(), goalsA));

        ratingA.setMatchesPlayed(ratingA.getMatchesPlayed() + 1);
        ratingB.setMatchesPlayed(ratingB.getMatchesPlayed() + 1);

        LocalDateTime now = LocalDateTime.now();

        ratingA.setLastUpdated(now);
        ratingB.setLastUpdated(now);

        ratingRepository.save(ratingA);
        ratingRepository.save(ratingB);
    }

    private double ema(double previous, double latest) {
        return previous + 0.25 * (latest - previous);
    }

    private int pointsFor(double score) {

        if (score == 1.0) {
            return 3;
        }

        if (score == 0.5) {
            return 1;
        }

        return 0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String resultLabel(int goalsA, int goalsB) {

        if (goalsA > goalsB) {
            return "PLAYER_A_WIN";
        }

        if (goalsA < goalsB) {
            return "PLAYER_B_WIN";
        }

        return "DRAW";
    }

    // =========================================================
    // CONFIDENCE CALCULATION
    // =========================================================

    private PredictionResponse.ConfidenceDto computeConfidence(
            PlayerFormStats formA,
            PlayerFormStats formB,
            HeadToHeadStats h2h,
            ESoccerPlayerRating ratingA,
            ESoccerPlayerRating ratingB) {

        List<String> factors = new ArrayList<>();

        double score = 100.0;

        int totalMatches = formA.matchesConsidered() + formB.matchesConsidered();

        if (totalMatches < 4) {
            score -= 30;
            factors.add(
                    "Limited recent match history (" + totalMatches + " matches total)");
        } else if (totalMatches < 8) {
            score -= 15;
            factors.add("Moderate recent match history");
        } else {
            factors.add("Good recent match history");
        }

        if (h2h.encounters() == 0) {
            score -= 20;
            factors.add("No head-to-head history");
        } else if (h2h.encounters() < 3) {
            score -= 10;
            factors.add(
                    "Small head-to-head sample (" + h2h.encounters() + " encounters)");
        } else {
            factors.add(
                    "Head-to-head history available (" + h2h.encounters() + " encounters)");
        }

        if (ratingA.getMatchesPlayed() < 5
                || ratingB.getMatchesPlayed() < 5) {

            score -= 15;
            factors.add("Player ratings still maturing");
        }

        if (formA.losingStreak() || formB.losingStreak()) {
            score -= 10;
            factors.add("Recent losing streak detected");
        }

        score = Math.max(0, Math.min(100, score));

        String level = score >= 70
                ? "HIGH"
                : (score >= 40 ? "MEDIUM" : "LOW");

        return new PredictionResponse.ConfidenceDto(
                level,
                score,
                factors);
    }

    // =========================================================
    // PREDICTION EXPLANATION
    // =========================================================

    private String buildExplanation(
            PlayerFormStats formA,
            PlayerFormStats formB,
            HeadToHeadStats h2h,
            OutcomeProbabilities outcome,
            GoalMarketProbabilities goals,
            PredictionResponse.ConfidenceDto confidence) {

        StringBuilder sb = new StringBuilder();

        sb.append(String.format(
                "%s has a %.0f%% win rate over their last %d matches, averaging %.1f goals scored and %.1f conceded.%n",
                formA.playerName(),
                formA.winPercentage(),
                formA.matchesConsidered(),
                formA.avgGoalsScored(),
                formA.avgGoalsConceded()));

        sb.append(String.format(
                "%s has a %.0f%% win rate over their last %d matches, averaging %.1f goals scored and %.1f conceded.%n",
                formB.playerName(),
                formB.winPercentage(),
                formB.matchesConsidered(),
                formB.avgGoalsScored(),
                formB.avgGoalsConceded()));

        if (h2h.encounters() > 0) {

            sb.append(String.format(
                    "Head-to-head: %s leads %d-%d (%d draws) over %d encounters, averaging %.1f goals per match.%n",
                    h2h.playerAWins() >= h2h.playerBWins()
                            ? h2h.playerA()
                            : h2h.playerB(),
                    h2h.playerAWins(),
                    h2h.playerBWins(),
                    h2h.draws(),
                    h2h.encounters(),
                    h2h.avgGoalsPerEncounter()));

        } else {
            sb.append(
                    "No head-to-head history available between these players.\n");
        }

        sb.append(String.format(
                "Model gives %.0f%% chance of %s win, %.0f%% draw, %.0f%% chance of %s win.%n",
                outcome.probPlayerAWin() * 100,
                formA.playerName(),
                outcome.probDraw() * 100,
                outcome.probPlayerBWin() * 100,
                formB.playerName()));

        sb.append(String.format(
                "Goals: Over 2.5 at %.0f%%, BTTS at %.0f%%.%n",
                goals.probOver2_5() * 100,
                goals.probBttsYes() * 100));

        sb.append(String.format(
                "Confidence: %s (%.0f%%). %s%n",
                confidence.level(),
                confidence.score(),
                String.join("; ", confidence.factors())));

        return sb.toString();
    }

    // =========================================================
    // SUPPORTING STATISTICS
    // =========================================================

    private String buildSupportingStats(
            PlayerFormStats formA,
            PlayerFormStats formB,
            HeadToHeadStats h2h,
            ESoccerPlayerRating ratingA,
            ESoccerPlayerRating ratingB) {

        return String.format(
                "{\"playerA\":{\"name\":\"%s\",\"rating\":%.0f,\"formPoints\":%d,\"avgGF\":%.2f,\"avgGA\":%.2f},"
                        + "\"playerB\":{\"name\":\"%s\",\"rating\":%.0f,\"formPoints\":%d,\"avgGF\":%.2f,\"avgGA\":%.2f},"
                        + "\"h2h\":{\"encounters\":%d,\"aWins\":%d,\"bWins\":%d,\"draws\":%d}}",

                formA.playerName(),
                ratingA.getRating(),
                ratingA.getFormPoints(),
                ratingA.getGoalsForAvg(),
                ratingA.getGoalsAgainstAvg(),

                formB.playerName(),
                ratingB.getRating(),
                ratingB.getFormPoints(),
                ratingB.getGoalsForAvg(),
                ratingB.getGoalsAgainstAvg(),

                h2h.encounters(),
                h2h.playerAWins(),
                h2h.playerBWins(),
                h2h.draws());
    }

    // =========================================================
    // ODDS COMPARISON
    // =========================================================

    private PredictionResponse.OddsComparisonDto buildOddsComparison(
            ESoccerMatch match,
            OutcomeProbabilities outcome,
            GoalMarketProbabilities goals,
            DoubleChanceProbabilities doubleChance) {

        List<ESoccerOdds> matchOdds = oddsRepository.findByMatchId(match.getId());

        if (matchOdds.isEmpty()) {
            return new PredictionResponse.OddsComparisonDto(
                    "NONE", "NONE",
                    0.0, 0.0, 0.0, 0.0, 0.0, false);
        }

        List<PredictionResponse.OddsComparisonDto> comparisons = new ArrayList<>();

        for (ESoccerOdds odds : matchOdds) {

            if (odds.getMarket() == null
                    || odds.getDecimalOdds() <= 1.0) {
                continue;
            }

            Double modelProbability = switch (odds.getMarket()) {

                case "HOME_WIN" ->
                    outcome.probPlayerAWin();

                case "AWAY_WIN" ->
                    outcome.probPlayerBWin();

                case "DRAW" ->
                    outcome.probDraw();

                case "OVER_1_5" ->
                    goals.probOver1_5();

                case "OVER_2_5" ->
                    goals.probOver2_5();

                case "OVER_3_5" ->
                    goals.probOver3_5();

                case "UNDER_2_5" ->
                    goals.probUnder2_5();

                case "BTTS_YES" ->
                    goals.probBttsYes();

                case "DOUBLE_CHANCE_1X" ->
                    doubleChance.prob1X();

                case "DOUBLE_CHANCE_X2" ->
                    doubleChance.probX2();

                case "DOUBLE_CHANCE_12" ->
                    doubleChance.prob12();

                default -> null;
            };

            if (modelProbability == null) {
                continue;
            }

            double impliedProbability = odds.impliedProbability();

            double difference = modelProbability - impliedProbability;

            double expectedValue = (modelProbability * odds.getDecimalOdds()) - 1.0;

            boolean valueBet = difference > 0.03 && expectedValue > 0;

            comparisons.add(
                    new PredictionResponse.OddsComparisonDto(
                            odds.getMarket(),
                            odds.getOutcome(),
                            odds.getDecimalOdds(),
                            impliedProbability,
                            modelProbability,
                            difference,
                            expectedValue,
                            valueBet));
        }

        if (comparisons.isEmpty()) {
            return new PredictionResponse.OddsComparisonDto(
                    "NONE", "NONE",
                    0.0, 0.0, 0.0, 0.0, 0.0, false);
        }

        // Return the first valid comparison.
        // The DTO accessor names are not assumed here.
        return comparisons.get(0);
    }

    // =========================================================
    // ENTITY RESOLUTION
    // =========================================================

    private ESoccerPlayer resolveOrCreatePlayer(
            String gamerId,
            String externalId) {

        return playerRepository
                .findByGamerIdIgnoreCase(gamerId)
                .orElseGet(() -> playerRepository.save(
                        new ESoccerPlayer(gamerId, externalId)));
    }

    private ESoccerTeam resolveOrCreateTeam(
            String name,
            String externalId) {

        return teamRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> teamRepository.save(
                        new ESoccerTeam(name, externalId)));
    }

    private ESoccerCompetition resolveOrCreateCompetition(
            String name,
            String externalId,
            boolean allowDraws) {

        return competitionRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> competitionRepository.save(
                        new ESoccerCompetition(
                                name, externalId, allowDraws)));
    }

    private ESoccerGameMode resolveOrCreateGameMode(
            String name,
            String externalId,
            Integer durationMinutes,
            boolean allowDraws) {

        return gameModeRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> gameModeRepository.save(
                        new ESoccerGameMode(
                                name,
                                externalId,
                                durationMinutes,
                                allowDraws)));
    }

    // =========================================================
    // PERSIST PREDICTION
    // =========================================================

    @Transactional
    public void persistPrediction(
            ESoccerMatch match,
            PredictionResponse response) {

        ESoccerPrediction prediction = new ESoccerPrediction(match);

        prediction.setProbPlayerAWin(
                response.outcome().probPlayerAWin());

        prediction.setProbDraw(
                response.outcome().probDraw());

        prediction.setProbPlayerBWin(
                response.outcome().probPlayerBWin());

        prediction.setProbOver1_5(
                response.goals().probOver1_5());

        prediction.setProbOver2_5(
                response.goals().probOver2_5());

        prediction.setProbOver3_5(
                response.goals().probOver3_5());

        prediction.setProbUnder2_5(
                response.goals().probUnder2_5());

        prediction.setProbBttsYes(
                response.goals().probBttsYes());

        prediction.setProbDoubleChance1X(
                response.doubleChance().prob1X());

        prediction.setProbDoubleChanceX2(
                response.doubleChance().probX2());

        prediction.setProbDoubleChance12(
                response.doubleChance().prob12());

        prediction.setConfidence(
                ESoccerPrediction.ConfidenceLevel.valueOf(
                        response.confidence()
                                .level()
                                .toUpperCase(Locale.ROOT)));

        prediction.setExplanation(response.explanation());

        prediction.setSupportingStatistics(
                response.supportingStatistics());

        prediction.setSampleSize(response.sampleSize());

        prediction.setActualResult(null);

        prediction.setCreatedAt(LocalDateTime.now());

        predictionRepository.save(prediction);
    }

    // =========================================================
    // MATCH SUMMARY MAPPING
    // =========================================================

    private MatchSummaryDto toSummaryDto(ESoccerMatch match) {
        return new MatchSummaryDto(
                match.getId(),
                match.getPlayerA().getGamerId(),
                match.getPlayerB().getGamerId(),
                match.getTeamA() != null ? match.getTeamA().getName() : "N/A",
                match.getTeamB() != null ? match.getTeamB().getName() : "N/A",
                match.getCompetition() != null ? match.getCompetition().getName() : "N/A",
                match.getGameMode() != null ? match.getGameMode().getName() : "N/A",
                match.getMatchDateTime(),
                match.isPlayed() ? "PLAYED" : "SCHEDULED",
                match.getGoalsA(),
                match.getGoalsB());
    }
}
