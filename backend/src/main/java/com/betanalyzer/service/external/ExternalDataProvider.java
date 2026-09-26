package com.betanalyzer.service.external;

import java.time.LocalDate;
import java.util.List;

/**
 * A source of real fixture and result data (SofaScore today; an official
 * feed, a paid API or a local file later). The sync service is written
 * against this interface only, so adding a second provider never touches the
 * import logic.
 *
 * <p>Implementations must throw {@link ExternalDataException} for anything
 * the caller should see as a clear message - a blocked host, a missing API
 * key, a malformed response - rather than leaking transport exceptions.
 */
public interface ExternalDataProvider {

    /** Short identifier used in logs and in the sync status, e.g. "sofascore". */
    String name();

    /** Human-readable endpoint the sync will talk to, shown in the UI. */
    String describeEndpoint();

    /** True when the provider has everything it needs to make a call (e.g. an API key). */
    boolean isConfigured();

    /**
     * Every fixture scheduled on one calendar day, in any competition the
     * provider covers. Cheapest way to top up the "upcoming matches" list.
     */
    List<ExternalEvent> scheduledEvents(LocalDate date);

    /**
     * Every match of one competition season, optionally only those already
     * played. Finished matches feed the strength model, so this is how the
     * Elo ratings get real.
     */
    List<ExternalEvent> seasonEvents(long tournamentId, long seasonId, boolean finishedOnly);
}
