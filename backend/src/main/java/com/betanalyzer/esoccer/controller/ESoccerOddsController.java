package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.dto.CreateOddsRequest;
import com.betanalyzer.esoccer.dto.PredictionResponse.OddsComparisonDto;
import com.betanalyzer.esoccer.entity.ESoccerOdds;
import com.betanalyzer.esoccer.service.ESoccerOddsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/esoccer/odds")
@RequiredArgsConstructor
public class ESoccerOddsController {

    private final ESoccerOddsService oddsService;

    @PostMapping
    public ESoccerOdds addOdds(@RequestBody CreateOddsRequest request) {
        return oddsService.addOdds(request);
    }

    @GetMapping("/match/{matchId}")
    public List<ESoccerOdds> oddsForMatch(@PathVariable Long matchId) {
        return oddsService.getOddsForMatch(matchId);
    }

    @GetMapping("/match/{matchId}/analysis")
    public List<OddsComparisonDto> analyzeOdds(@PathVariable Long matchId,
                                               @RequestParam String market,
                                               @RequestParam String outcome,
                                               @RequestParam double modelProbability) {
        return List.of(oddsService.analyzeOdds(matchId, market, outcome, modelProbability))
                .stream()
                .map(a -> new OddsComparisonDto(
                        a.market(), a.outcome(), a.decimalOdds(),
                        a.impliedProbability(), a.modelProbability(),
                        a.difference(), a.expectedValue(), a.valueBet()))
                .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/match/{matchId}/margin")
    public Double margin(@PathVariable Long matchId, @RequestParam String market) {
        return oddsService.calculateMargin(matchId, market);
    }
}
