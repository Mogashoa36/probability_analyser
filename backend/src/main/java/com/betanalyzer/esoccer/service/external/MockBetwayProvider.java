package com.betanalyzer.esoccer.service.external;

import com.betanalyzer.esoccer.dto.ExternalESoccerEvent;
import com.betanalyzer.esoccer.dto.ExternalESoccerOdds;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

/**
 * No-op provider used when no real Betway feed is configured. The application
 * continues to function using manually entered or CSV/JSON-imported data.
 */
@Component
public class MockBetwayProvider implements BetwayDataProvider {

    @Override
    public String name() { return "mock"; }

    @Override
    public String describeEndpoint() { return "No external provider configured (manual/CSV/JSON only)"; }

    @Override
    public boolean isConfigured() { return false; }

    @Override
    public List<ExternalESoccerEvent> scheduledEvents(LocalDate date) { return List.of(); }

    @Override
    public List<ExternalESoccerEvent> seasonEvents(String competitionId, String seasonId, boolean finishedOnly) {
        return List.of();
    }

    @Override
    public List<ExternalESoccerOdds> oddsForEvent(String externalEventId) { return List.of(); }
}
