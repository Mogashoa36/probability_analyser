package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.dto.HeadToHeadResponse;
import com.betanalyzer.esoccer.dto.PlayerFormResponse;
import com.betanalyzer.esoccer.service.ESoccerPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/esoccer/analysis")
@RequiredArgsConstructor
public class ESoccerAnalysisController {

    private final ESoccerPredictionService predictionService;

    @GetMapping("/players/{id}/form")
    public PlayerFormResponse form(@PathVariable Long id) {
        return predictionService.getPlayerForm(id);
    }

    @GetMapping("/players/{id}/history")
    public PlayerFormResponse history(@PathVariable Long id) {
        return predictionService.getPlayerForm(id);
    }

    @GetMapping("/matches/{id}/head-to-head")
    public HeadToHeadResponse headToHead(@PathVariable Long matchId) {
        // The matchId path variable actually refers to a match; we need both player IDs.
        // For simplicity, client passes player IDs as query params.
        throw new UnsupportedOperationException("Use /api/esoccer/analysis/head-to-head?playerA=...&playerB=...");
    }

    @GetMapping("/head-to-head")
    public HeadToHeadResponse headToHead(@RequestParam Long playerA, @RequestParam Long playerB) {
        return predictionService.getHeadToHead(playerA, playerB);
    }
}
