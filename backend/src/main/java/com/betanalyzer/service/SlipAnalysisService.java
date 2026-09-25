package com.betanalyzer.service;

import com.betanalyzer.dto.SelectionResultDto;
import com.betanalyzer.dto.SlipAnalysisRequestDto;
import com.betanalyzer.dto.SlipAnalysisResponseDto;
import com.betanalyzer.entity.SlipSelection.Market;
import com.betanalyzer.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates slip analysis: parses input -> resolves teams -> models each
 * selection's true probability -> combines the slip -> generates refined,
 * actionable suggestions for improving it.
 */
@Service
@RequiredArgsConstructor
public class SlipAnalysisService {

    private final SlipParserService slipParserService;
    private final TeamStrengthService teamStrengthService;
    private final ProbabilityCalculationService probabilityCalculationService;

    public SlipAnalysisResponseDto analyze(SlipAnalysisRequestDto request) {
        List<SlipParserService.ParsedSelection> parsed;

        if (request.getRawText() != null && !request.getRawText().isBlank()) {
            parsed = slipParserService.parseSlipText(request.getRawText());
        } else {
            parsed = new ArrayList<>();
            if (request.getSelections() != null) {
                for (var s : request.getSelections()) {
                    parsed.add(new SlipParserService.ParsedSelection(
                            s.getHomeTeam(), s.getAwayTeam(),
                            mapMarketSafe(s.getMarket()), s.getOdds(), null));
                }
            }
        }

        List<SelectionResultDto> results = new ArrayList<>();
        double combinedProbability = 1.0;
        double totalOdds = 1.0;

        for (var sel : parsed) {
            if (sel.getWarning() != null) {
                results.add(new SelectionResultDto(sel.getHomeTeam(), sel.getAwayTeam(), "UNKNOWN",
                        0, 0, 0, 0, "LOW", sel.getWarning()));
                continue;
            }

            Team home = teamStrengthService.resolveOrDefault(sel.getHomeTeam());
            Team away = teamStrengthService.resolveOrDefault(sel.getAwayTeam());
            boolean knownFixture = teamStrengthService.isKnownTeam(sel.getHomeTeam())
                    && teamStrengthService.isKnownTeam(sel.getAwayTeam());

            // Report the canonical club name so a pasted "Man City" reads back as
            // "Manchester City" - the name the probabilities were actually based on.
            String homeName = home.getName();
            String awayName = away.getName();

            Market market = sel.getMarket() == null || sel.getMarket() == Market.UNKNOWN
                    ? Market.HOME_WIN : sel.getMarket();

            double modelProb = probabilityCalculationService.computeMarketProbability(home, away, market);
            double odds = sel.getOdds() != null ? sel.getOdds() : (modelProb > 0 ? round(1 / modelProb) : 0);
            double impliedProb = odds > 0 ? 1 / odds : 0;
            double edge = modelProb - impliedProb;

            String confidence = knownFixture
                    ? (Math.abs(edge) > 0.08 ? "HIGH" : "MEDIUM")
                    : "LOW";

            String note = buildNote(sel, market, modelProb, impliedProb, edge, knownFixture);

            results.add(new SelectionResultDto(
                    homeName, awayName, market.name(),
                    odds, round(modelProb * 100), round(impliedProb * 100), round(edge * 100),
                    confidence, note));

            combinedProbability *= modelProb;
            totalOdds *= odds;
        }

        double impliedOverall = totalOdds > 0 ? 1 / totalOdds : 0;
        String riskRating = riskRatingFor(combinedProbability);

        List<SelectionResultDto> weakest = results.stream()
                .filter(r -> r.getOdds() > 0)
                .sorted(Comparator.comparingDouble(SelectionResultDto::getModelProbabilityPct))
                .limit(2)
                .toList();

        List<String> suggestions = buildSuggestions(results, weakest, combinedProbability, parsed.size());

        return new SlipAnalysisResponseDto(
                results, round(totalOdds), round(combinedProbability * 100),
                round(impliedOverall * 100), riskRating, suggestions, weakest);
    }

    private Market mapMarketSafe(String raw) {
        if (raw == null) return Market.UNKNOWN;
        try {
            return Market.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return Market.UNKNOWN;
        }
    }

    private String buildNote(SlipParserService.ParsedSelection sel, Market market,
                              double modelProb, double impliedProb, double edge, boolean knownFixture) {
        StringBuilder sb = new StringBuilder();
        if (!knownFixture) {
            sb.append("One or both teams aren't in the strength database yet, so this uses league-average figures. ");
        }
        if (edge > 0.05) {
            sb.append(String.format(Locale.ROOT,
                    "Model rates this %.0f%% vs an implied %.0f%% from the odds - looks like value.",
                    modelProb * 100, impliedProb * 100));
        } else if (edge < -0.05) {
            sb.append(String.format(Locale.ROOT,
                    "Model rates this only %.0f%% vs an implied %.0f%% - the odds are shorter than the model justifies.",
                    modelProb * 100, impliedProb * 100));
        } else {
            sb.append("Model probability is roughly in line with the odds on offer.");
        }
        return sb.toString();
    }

    private String riskRatingFor(double combinedProbability) {
        if (combinedProbability >= 0.45) return "LOW";
        if (combinedProbability >= 0.25) return "MEDIUM";
        if (combinedProbability >= 0.10) return "HIGH";
        return "VERY HIGH";
    }

    private List<String> buildSuggestions(List<SelectionResultDto> results, List<SelectionResultDto> weakest,
                                           double combinedProbability, int legCount) {
        List<String> suggestions = new ArrayList<>();

        for (SelectionResultDto r : results) {
            if (r.getEdgePct() < -5) {
                suggestions.add(String.format(Locale.ROOT,
                        "Consider dropping %s vs %s (%s) - negative edge of %.1f%%, the model thinks the odds are too short.",
                        r.getHomeTeam(), r.getAwayTeam(), r.getMarket(), r.getEdgePct()));
            }
        }

        if (!weakest.isEmpty()) {
            SelectionResultDto w = weakest.get(0);
            double withoutWeakest = w.getModelProbabilityPct() > 0
                    ? combinedProbability / (w.getModelProbabilityPct() / 100.0) : combinedProbability;
            suggestions.add(String.format(Locale.ROOT,
                    "%s vs %s (%s) is the weakest leg at %.0f%% - removing it would lift the slip's combined win chance to roughly %.0f%%.",
                    w.getHomeTeam(), w.getAwayTeam(), w.getMarket(), w.getModelProbabilityPct(),
                    Math.min(100, withoutWeakest * 100)));
        }

        if (legCount >= 5) {
            suggestions.add(String.format(Locale.ROOT,
                    "This slip has %d legs - each additional leg multiplies risk. Splitting it into two smaller multiples usually gives a much better realistic return-on-risk than one long acca.",
                    legCount));
        }

        boolean anyUnknown = results.stream().anyMatch(r -> r.getConfidence().equals("LOW"));
        if (anyUnknown) {
            suggestions.add("Some teams aren't in the database yet - add their recent results so the model can rate those legs on real form rather than league averages.");
        }

        boolean anyStrong = results.stream().anyMatch(r -> r.getModelProbabilityPct() > 70 && r.getEdgePct() > 0);
        if (anyStrong) {
            suggestions.add("At least one leg is both high-probability and positive-edge - that's your anchor leg; the rest of the slip should be judged against how much it dilutes that one.");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("This slip looks reasonably balanced - no single leg stands out as a clear weak point.");
        }
        return suggestions;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
