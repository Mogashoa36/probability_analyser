package com.betanalyzer.service.external;

import java.time.Instant;

/**
 * One fixture (or played match) from an upstream provider, in provider-neutral
 * terms. {@code homeGoals}/{@code awayGoals} are null until the match is
 * finished, {@code status} is the provider's own wording, and {@code cancelled}
 * marks a fixture that was called off - which is neither a result nor a fixture
 * still to be played.
 */
public record ExternalEvent(
        String externalId,
        String competition,
        ExternalTeamRef homeTeam,
        ExternalTeamRef awayTeam,
        Instant kickOff,
        Integer homeGoals,
        Integer awayGoals,
        String status,
        boolean finished,
        boolean cancelled
) {
    public boolean isUsable() {
        return externalId != null
                && homeTeam != null && awayTeam != null
                && kickOff != null
                && homeTeam.externalId() != null && awayTeam.externalId() != null;
    }
}
