package com.betanalyzer.controller;

import com.betanalyzer.dto.RecommendationDto;
import com.betanalyzer.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints backing the "Recommended Matches" page.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** Recommendations from fixtures already stored in the database. */
    @GetMapping
    public List<RecommendationDto> recommended(@RequestParam(defaultValue = "10") int limit) {
        return recommendationService.recommendFromStoredFixtures(limit);
    }

    /** Recommendations from a pasted list of upcoming fixtures, e.g. "Team A vs Team B" per line. */
    @PostMapping("/from-list")
    public List<RecommendationDto> fromList(@RequestBody Map<String, Object> body) {
        String rawText = String.valueOf(body.getOrDefault("rawText", ""));
        int limit = body.get("limit") != null ? Integer.parseInt(String.valueOf(body.get("limit"))) : 10;
        return recommendationService.recommendFromRawFixtureList(rawText, limit);
    }
}
