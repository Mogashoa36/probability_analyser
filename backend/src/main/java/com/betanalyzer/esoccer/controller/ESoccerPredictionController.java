package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.dto.PredictionResponse;
import com.betanalyzer.esoccer.dto.MatchSummaryDto;
import com.betanalyzer.esoccer.service.ESoccerPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/esoccer/predictions")
@RequiredArgsConstructor
public class ESoccerPredictionController {

    private final ESoccerPredictionService predictionService;

    @GetMapping("/{matchId}")
    public PredictionResponse analyze(@PathVariable Long matchId) {
        return predictionService.predict(matchId);
    }

    @GetMapping
    public List<MatchSummaryDto> upcoming() {
        return predictionService.listUpcoming();
    }
}
