package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.dto.CreateOddsRequest;
import com.betanalyzer.esoccer.dto.PredictionResponse.OddsComparisonDto;
import com.betanalyzer.esoccer.entity.ESoccerMatch;
import com.betanalyzer.esoccer.entity.ESoccerOdds;
import com.betanalyzer.esoccer.repository.ESoccerMatchRepository;
import com.betanalyzer.esoccer.repository.ESoccerOddsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages bookmaker odds for eSoccer matches and calculates expected value
 * against model probabilities. Keeps bookmaker odds separate from model output.
 */
@Slf4j
@Service
public class ESoccerOddsService {

    private final ESoccerOddsRepository oddsRepository;
    private final ESoccerMatchRepository matchRepository;

    public ESoccerOddsService(ESoccerOddsRepository oddsRepository, ESoccerMatchRepository matchRepository) {
        this.oddsRepository = oddsRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public ESoccerOdds addOdds(CreateOddsRequest request) {
        ESoccerMatch match = matchRepository.findById(request.matchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + request.matchId()));

        if (request.decimalOdds() <= 1.0) {
            throw new IllegalArgumentException("Decimal odds must be greater than 1.0");
        }

        ESoccerOdds odds = new ESoccerOdds(
                match, request.market(), request.outcome(),
                request.decimalOdds(), request.bookmaker());
        return oddsRepository.save(odds);
    }

    @Transactional(readOnly = true)
    public List<ESoccerOdds> getOddsForMatch(Long matchId) {
        return oddsRepository.findByMatchId(matchId);
    }

    /** Calculate implied probability and EV for a given market/outcome vs model probability. */
    @Transactional(readOnly = true)
    public OddsAnalysis analyzeOdds(Long matchId, String market, String outcome, double modelProbability) {
        Optional<ESoccerOdds> oddsOpt = oddsRepository.findByMatchIdAndMarketAndOutcome(matchId, market, outcome);
        if (oddsOpt.isEmpty()) {
            throw new IllegalArgumentException("No odds found for " + market + " " + outcome + " on match " + matchId);
        }

        ESoccerOdds odds = oddsOpt.get();
        double implied = odds.impliedProbability();
        double difference = modelProbability - implied;
        double ev = modelProbability * odds.getDecimalOdds() - 1.0;

        // Only flag as value when there is a meaningful edge AND positive EV
        // Also account for bookmaker margin: require edge > 3% to avoid noise
        boolean valueBet = difference > 0.03 && ev > 0;

        return new OddsAnalysis(
                odds.getMarket(), odds.getOutcome(), odds.getDecimalOdds(),
                implied, modelProbability, difference, ev, valueBet,
                odds.getBookmaker(), odds.getOddsDate()
        );
    }

    /** Calculate total bookmaker margin across all outcomes of a market. */
    @Transactional(readOnly = true)
    public double calculateMargin(Long matchId, String market) {
        List<ESoccerOdds> marketOdds = oddsRepository.findByMatchId(matchId).stream()
                .filter(o -> o.getMarket().equals(market))
                .collect(Collectors.toList());

        if (marketOdds.isEmpty()) return 0.0;

        double sumImplied = marketOdds.stream()
                .mapToDouble(ESoccerOdds::impliedProbability)
                .sum();

        return Math.max(0, sumImplied - 1.0); // margin = sum of implied probs - 1
    }

    // ---------------------------------------------------------------

    public record OddsAnalysis(
            String market, String outcome, double decimalOdds,
            double impliedProbability, double modelProbability,
            double difference, double expectedValue, boolean valueBet,
            String bookmaker, LocalDateTime oddsDate
    ) {}
}
