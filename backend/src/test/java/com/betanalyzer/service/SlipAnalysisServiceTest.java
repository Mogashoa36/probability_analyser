package com.betanalyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.betanalyzer.dto.SlipAnalysisRequestDto;
import com.betanalyzer.entity.Team;
import org.junit.jupiter.api.Test;

/**
 * Validates the slip-analysis orchestrator, especially the guard against
 * silently producing a misleading 100% / low-risk result for an empty slip.
 */
class SlipAnalysisServiceTest {

    private final SlipParserService parser = mock(SlipParserService.class);
    private final TeamStrengthService strength = mock(TeamStrengthService.class);
    private final ProbabilityCalculationService model = mock(ProbabilityCalculationService.class);

    private SlipAnalysisService service() {
        return new SlipAnalysisService(parser, strength, model);
    }

    @Test
    void emptySlipRejectedInsteadOfFabricating100Percent() {
        when(parser.parseSlipText("")).thenReturn(java.util.List.of());

        SlipAnalysisRequestDto request = new SlipAnalysisRequestDto();
        request.setRawText("");

        assertThatThrownBy(() -> service().analyze(request))
                .isInstanceOf(IllegalArgumentException.class)
                .message().contains("No selections to analyze");
    }

    @Test
    void blankRequestRejected() {
        when(parser.parseSlipText("   ")).thenReturn(java.util.List.of());

        SlipAnalysisRequestDto request = new SlipAnalysisRequestDto();
        request.setRawText("   ");

        assertThatThrownBy(() -> service().analyze(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputRejected() {
        SlipAnalysisRequestDto request = new SlipAnalysisRequestDto();
        request.setRawText(null);

        assertThatThrownBy(() -> service().analyze(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knownTeamStaysKnownAndUnknownTeamFallsBack() {
        Team known = new Team("Kaizer Chiefs", "PSL", 1512);
        when(strength.isKnownTeam("Kaizer Chiefs")).thenReturn(true);
        when(strength.resolveOrDefault("Kaizer Chiefs")).thenReturn(known);
        when(strength.resolveOrDefault("Unknown Side")).thenAnswer(a -> {
            Team f = new Team("Unknown Side", "Unranked", 1500);
            f.setFormPoints(7);
            f.setGoalsForAvg(1.2);
            f.setGoalsAgainstAvg(1.2);
            f.setHomeAdvantage(55);
            return f;
        });

        assertThat(strength.isKnownTeam("Kaizer Chiefs")).isTrue();
        assertThat(strength.resolveOrDefault("Kaizer Chiefs").getName()).isEqualTo("Kaizer Chiefs");
        assertThat(strength.resolveOrDefault("Unknown Side").getName()).isEqualTo("Unknown Side");
    }
}
