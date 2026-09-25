package com.betanalyzer.service;

import com.betanalyzer.dto.RecommendationDto;
import com.betanalyzer.entity.Match;
import com.betanalyzer.entity.SlipSelection.Market;
import com.betanalyzer.entity.Team;
import com.betanalyzer.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds the "Recommended matches" page: scans upcoming fixtures (either
 * stored ones, or a pasted list) and ranks them by how confidently the
 * model can call an outcome - i.e. how lopsided the match is, not just
 * which side is favoured. A 90/5/5 split is a much safer recommendation
 * than a 45/30/25 one even though both favour the same team.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MatchRepository matchRepository;
    private final TeamStrengthService teamStrengthService;
    private final ProbabilityCalculationService probabilityCalculationService;
    private final SlipParserService slipParserService;

    /** Recommendations built from fixtures already stored in the database. */
    public List<RecommendationDto> recommendFromStoredFixtures(int limit) {
        List<Match> upcoming = matchRepository.findByStatus(Match.MatchStatus.SCHEDULED);
        List<RecommendationDto> recs = new ArrayList<>();
        for (Match m : upcoming) {
            recs.add(buildRecommendation(m.getId(), m.getHomeTeam(), m.getAwayTeam(), m.getLeague()));
        }
        return recs.stream()
                .sorted(Comparator.comparingDouble(RecommendationDto::getConfidenceScore).reversed())
                .limit(limit)
                .toList();
    }

    /** Recommendations built from a pasted list of fixtures, e.g. "Team A vs Team B" per line. */
    public List<RecommendationDto> recommendFromRawFixtureList(String rawText, int limit) {
        List<RecommendationDto> recs = new ArrayList<>();
        for (var parsed : slipParserService.parseSlipText(rawText)) {
            if (parsed.getWarning() != null) continue;
            recs.add(buildRecommendation(null, parsed.getHomeTeam(), parsed.getAwayTeam(), null));
        }
        return recs.stream()
                .sorted(Comparator.comparingDouble(RecommendationDto::getConfidenceScore).reversed())
                .limit(limit)
                .toList();
    }

    private RecommendationDto buildRecommendation(Long matchId, Team homeStored, Team awayStored, String league) {
        return buildRecommendationInternal(matchId, homeStored, awayStored,
                league != null ? league : homeStored.getLeague());
    }

    private RecommendationDto buildRecommendation(Long matchId, String homeName, String awayName, String league) {
        Team home = teamStrengthService.resolveOrDefault(homeName);
        Team away = teamStrengthService.resolveOrDefault(awayName);
        return buildRecommendationInternal(matchId, home, away, league != null ? league : home.getLeague());
    }

    private RecommendationDto buildRecommendationInternal(Long matchId, Team home, Team away, String league) {
        var outcome = probabilityCalculationService.computeOutcomeProbabilities(home, away);

        // Find the most likely of the three result markets.
        Market bestMarket;
        double bestProb;
        if (outcome.homeWin() >= outcome.draw() && outcome.homeWin() >= outcome.awayWin()) {
            bestMarket = Market.HOME_WIN;
            bestProb = outcome.homeWin();
        } else if (outcome.awayWin() >= outcome.draw()) {
            bestMarket = Market.AWAY_WIN;
            bestProb = outcome.awayWin();
        } else {
            bestMarket = Market.DRAW;
            bestProb = outcome.draw();
        }

        // Also check whether a goals market is an even stronger, more confident pick.
        double overProb = probabilityCalculationService.probabilityOverGoals(home, away, 2);
        if (overProb > bestProb && overProb > 0.62) {
            bestMarket = Market.OVER_2_5;
            bestProb = overProb;
        } else if ((1 - overProb) > bestProb && (1 - overProb) > 0.62) {
            bestMarket = Market.UNDER_2_5;
            bestProb = 1 - overProb;
        }

        // Confidence score rewards both a high probability AND a clear gap to the next best outcome
        // (a lopsided match is a safer recommendation than a close one, even at the same top probability).
        double secondBest = secondHighest(outcome.homeWin(), outcome.draw(), outcome.awayWin());
        double gap = bestProb - secondBest;
        double confidenceScore = Math.round((bestProb * 70 + gap * 30) * 100.0) / 100.0;

        String reasoning = buildReasoning(home, away, bestMarket, bestProb, gap);

        return new RecommendationDto(matchId, home.getName(), away.getName(),
                league, bestMarket.name(), Math.round(bestProb * 10000.0) / 100.0, confidenceScore, reasoning);
    }

    private double secondHighest(double a, double b, double c) {
        double max = Math.max(a, Math.max(b, c));
        double min = Math.min(a, Math.min(b, c));
        return a + b + c - max - min;
    }

    private String buildReasoning(Team home, Team away, Market market, double prob, double gap) {
        double eloDiff = Math.round(home.getEloRating() + home.getHomeAdvantage() - away.getEloRating());
        String favouredSide = switch (market) {
            case HOME_WIN -> home.getName() + " at home";
            case AWAY_WIN -> away.getName() + " away";
            case DRAW -> "a draw";
            case OVER_2_5 -> "over 2.5 goals";
            case UNDER_2_5 -> "under 2.5 goals";
            default -> market.name();
        };
        return String.format(Locale.ROOT,
                "%s rated at %.0f%% (rating edge of %.0f pts, %.0f pts clear of the next most likely outcome). Based on Elo strength, home advantage, recent form and expected goals.",
                capitalize(favouredSide), prob * 100, eloDiff, gap * 100);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
