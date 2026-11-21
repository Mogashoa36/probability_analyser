package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.entity.ESoccerPrediction;
import com.betanalyzer.esoccer.repository.ESoccerPredictionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Backtesting capability: evaluates stored predictions against actual results.
 * No data leakage - predictions only ever used pre-kickoff data.
 */
@Slf4j
@Service
public class ESoccerBacktestingService {

    private final ESoccerPredictionRepository predictionRepository;

    public ESoccerBacktestingService(ESoccerPredictionRepository predictionRepository) {
        this.predictionRepository = predictionRepository;
    }

    /** Evaluate all predictions that have actual results recorded. */
    @Transactional(readOnly = true)
    public BacktestReport evaluate() {
        List<ESoccerPrediction> predictions = predictionRepository.findByActualResultIsNotNull();
        if (predictions.isEmpty()) {
            return new BacktestReport(0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), List.of());
        }

        int correct = 0;
        double logLoss = 0.0;
        double brierSum = 0.0;
        int total = predictions.size();

        Map<String, int[]> marketAccuracy = new HashMap<>(); // market -> [correct, total]
        Map<String, int[]> confidenceAccuracy = new HashMap<>(); // level -> [correct, total]
        Map<String, int[]> gameModeAccuracy = new HashMap<>();

        List<CalibrationPoint> calibration = new ArrayList<>();

        for (ESoccerPrediction p : predictions) {
            String actual = p.getActualResult();
            boolean outcomeCorrect = isOutcomeCorrect(p, actual);
            if (outcomeCorrect) correct++;

            // Brier score on the primary outcome
            double pA = p.getProbPlayerAWin() != null ? p.getProbPlayerAWin() : 0.0;
            double pD = p.getProbDraw() != null ? p.getProbDraw() : 0.0;
            double pB = p.getProbPlayerBWin() != null ? p.getProbPlayerBWin() : 0.0;

            double actualA = "PLAYER_A_WIN".equals(actual) ? 1.0 : 0.0;
            double actualD = "DRAW".equals(actual) ? 1.0 : 0.0;
            double actualB = "PLAYER_B_WIN".equals(actual) ? 1.0 : 0.0;

            brierSum += Math.pow(pA - actualA, 2) + Math.pow(pD - actualD, 2) + Math.pow(pB - actualB, 2);

            // Log loss (avoid log(0))
            logLoss -= Math.log(clamp(pA, 0.001, 0.999)) * actualA;
            logLoss -= Math.log(clamp(pD, 0.001, 0.999)) * actualD;
            logLoss -= Math.log(clamp(pB, 0.001, 0.999)) * actualB;

            // Market breakdown - use best predicted market
            String bestMarket = bestMarket(p);
            marketAccuracy.computeIfAbsent(bestMarket, k -> new int[2]);
            marketAccuracy.get(bestMarket)[1]++;
            if (outcomeCorrect) marketAccuracy.get(bestMarket)[0]++;

            String level = p.getConfidence() != null ? p.getConfidence().name() : "UNKNOWN";
            confidenceAccuracy.computeIfAbsent(level, k -> new int[2]);
            confidenceAccuracy.get(level)[1]++;
            if (outcomeCorrect) confidenceAccuracy.get(level)[0]++;

            calibration.add(new CalibrationPoint(
                    bestMarket, level,
                    Math.max(Math.max(pA, pD), pB),
                    outcomeCorrect
            ));
        }

        double accuracy = (double) correct / total * 100;
        double avgLogLoss = logLoss / total;
        double avgBrier = brierSum / total / 3.0; // 3 outcomes

        Map<String, Double> marketPct = new HashMap<>();
        marketAccuracy.forEach((k, v) -> marketPct.put(k, v[1] > 0 ? (double) v[0] / v[1] * 100 : 0));

        Map<String, Double> confidencePct = new HashMap<>();
        confidenceAccuracy.forEach((k, v) -> confidencePct.put(k, v[1] > 0 ? (double) v[0] / v[1] * 100 : 0));

        Map<String, Double> gameModePct = new HashMap<>();
        gameModeAccuracy.forEach((k, v) -> gameModePct.put(k, v[1] > 0 ? (double) v[0] / v[1] * 100 : 0));

        return new BacktestReport(total, correct, round(accuracy, 1),
                round(avgLogLoss, 4), round(avgBrier, 4),
                total, marketPct, confidencePct, gameModePct, calibration);
    }

    // ---------------------------------------------------------------

    private boolean isOutcomeCorrect(ESoccerPrediction p, String actual) {
        double pA = p.getProbPlayerAWin() != null ? p.getProbPlayerAWin() : 0;
        double pD = p.getProbDraw() != null ? p.getProbDraw() : 0;
        double pB = p.getProbPlayerBWin() != null ? p.getProbPlayerBWin() : 0;

        String predicted;
        if (pA >= pD && pA >= pB) predicted = "PLAYER_A_WIN";
        else if (pB >= pD) predicted = "PLAYER_B_WIN";
        else predicted = "DRAW";

        return predicted.equals(actual);
    }

    private String bestMarket(ESoccerPrediction p) {
        double pA = p.getProbPlayerAWin() != null ? p.getProbPlayerAWin() : 0;
        double pD = p.getProbDraw() != null ? p.getProbDraw() : 0;
        double pB = p.getProbPlayerBWin() != null ? p.getProbPlayerBWin() : 0;
        double o15 = p.getProbOver1_5() != null ? p.getProbOver1_5() : 0;
        double o25 = p.getProbOver2_5() != null ? p.getProbOver2_5() : 0;
        double btts = p.getProbBttsYes() != null ? p.getProbBttsYes() : 0;

        double max = Math.max(Math.max(Math.max(pA, pD), pB), Math.max(Math.max(o15, o25), btts));
        if (max == pA) return "PLAYER_A_WIN";
        if (max == pB) return "PLAYER_B_WIN";
        if (max == pD) return "DRAW";
        if (max == o25) return "OVER_2_5";
        if (max == o15) return "OVER_1_5";
        return "BTTS_YES";
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round(double v, int places) {
        double scale = Math.pow(10, places);
        return Math.round(v * scale) / scale;
    }

    // ---------------------------------------------------------------

    public record BacktestReport(
            int totalPredictions, int correctPredictions, double accuracyPct,
            double avgLogLoss, double avgBrierScore,
            int evaluatedCount,
            Map<String, Double> marketAccuracy,
            Map<String, Double> confidenceAccuracy,
            Map<String, Double> gameModeAccuracy,
            List<CalibrationPoint> calibrationPoints
    ) {}

    public record CalibrationPoint(
            String market, String confidenceLevel, double predictedProbability, boolean correct
    ) {}
}
