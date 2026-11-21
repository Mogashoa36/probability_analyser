package com.betanalyzer.esoccer.service.external;

import com.betanalyzer.esoccer.dto.ExternalESoccerEvent;
import com.betanalyzer.esoccer.dto.ExternalESoccerOdds;
import java.time.LocalDate;
import java.util.List;

/**
 * A source of real eSoccer fixture, result and odds data (Betway by default;
 * a CSV/JSON file or another official feed later). The import service is
 * written against this interface only, so adding a second provider never
 * touches the import logic.
 *
 * <p>No scraping: implementations must use an officially supported API or
 * permitted data feed. When no provider is configured the import service
 * falls back to manual / CSV / JSON entry and the app continues to work.
 */
public interface BetwayDataProvider {

    String name();

    String describeEndpoint();

    boolean isConfigured();

    /** Upcoming eSoccer fixtures for one calendar day. */
    List<ExternalESoccerEvent> scheduledEvents(LocalDate date);

    /** Finished eSoccer matches for one competition/season. */
    List<ExternalESoccerEvent> seasonEvents(String competitionId, String seasonId, boolean finishedOnly);

    /** Bookmaker odds for upcoming fixtures, when the provider offers them. */
    List<ExternalESoccerOdds> oddsForEvent(String externalEventId);
}
