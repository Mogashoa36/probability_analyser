package com.betanalyzer.controller;

import com.betanalyzer.dto.SlipAnalysisRequestDto;
import com.betanalyzer.dto.SlipAnalysisResponseDto;
import com.betanalyzer.service.SlipAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints backing the "Analyse Slip" page: paste a slip (free text or
 * structured JSON), get back a per-leg and combined win-probability
 * breakdown plus refined suggestions.
 */
@RestController
@RequestMapping("/api/slips")
@RequiredArgsConstructor
public class SlipController {

    private final SlipAnalysisService slipAnalysisService;

    @PostMapping("/analyze")
    public SlipAnalysisResponseDto analyze(@RequestBody SlipAnalysisRequestDto request) {
        return slipAnalysisService.analyze(request);
    }
}
