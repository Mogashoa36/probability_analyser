package com.betanalyzer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Input payload for POST /api/slips/analyze.
 * Either supply `rawText` (a pasted slip, one selection per line) OR
 * a structured `selections` list - whichever the client has on hand.
 */
@Getter
@Setter
public class SlipAnalysisRequestDto {

    /**
     * Free-text paste, e.g.:
     *   Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10
     *   Man City vs Arsenal - Over 2.5 @ 1.65
     *   Real Madrid vs Barcelona - Draw @ 3.40
     */
    private String rawText;

    /** Structured alternative to rawText - used if rawText is blank. */
    private List<SelectionInputDto> selections;

    @Getter
    @Setter
    public static class SelectionInputDto {
        private String homeTeam;
        private String awayTeam;
        private String market;   // e.g. HOME_WIN, AWAY_WIN, DRAW, OVER_2_5...
        private double odds;
    }
}
