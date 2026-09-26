package com.betanalyzer.controller;

import com.betanalyzer.dto.SyncResultDto;
import com.betanalyzer.dto.SyncStatusDto;
import com.betanalyzer.service.SyncService;
import com.betanalyzer.service.external.ExternalDataException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoints for pulling real fixtures and results from an external provider
 * (SofaScore by default) into the local database.
 *
 * <p>These are POSTs because they change the database - the team's ratings and
 * goal averages move as finished results come in. Nothing is fetched on boot,
 * so a sync is always an explicit choice.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /** What the provider connection looks like, and what the last run did. */
    @GetMapping("/status")
    public SyncStatusDto status() {
        return syncService.status();
    }

    /**
     * Every fixture scheduled on one day, across all competitions. The
     * cheapest way to fill the "Recommended Matches" list.
     */
    @PostMapping("/day")
    public SyncResultDto syncDay(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return syncService.syncDay(date == null ? LocalDate.now() : date);
    }

    /** Next few days in one call, for keeping the upcoming list topped up. */
    @PostMapping("/days")
    public SyncResultDto syncDays(@RequestParam(defaultValue = "7") int days) {
        int clamped = Math.max(1, Math.min(days, 30));
        int fixtures = 0;
        int teamsCreated = 0;
        int teamsUpdated = 0;
        int updated = 0;
        int results = 0;
        int skipped = 0;
        boolean success = false;
        StringBuilder problems = new StringBuilder();

        for (int offset = 0; offset < clamped; offset++) {
            SyncResultDto day = syncService.syncDay(LocalDate.now().plusDays(offset));
            fixtures += day.getFixturesCreated() + day.getFixturesUpdated();
            teamsCreated += day.getTeamsCreated();
            teamsUpdated += day.getTeamsUpdated();
            updated += day.getFixturesUpdated();
            results += day.getResultsRecorded();
            skipped += day.getSkipped();
            success |= day.isSuccess();
            if (!day.isSuccess() && day.getMessage() != null) {
                if (problems.length() > 0) problems.append(' ');
                problems.append(day.getMessage());
            }
        }

        return new SyncResultDto(
                "sofascore",
                "the next " + clamped + " day(s)",
                java.time.LocalDateTime.now().toString(),
                teamsCreated, teamsUpdated,
                Math.max(0, fixtures - updated), updated, results, skipped,
                success,
                success ? "Pulled " + fixtures + " fixtures across the next " + clamped + " day(s)."
                        : problems.toString());
    }

    /**
     * One competition season. With {@code results=true} the played matches are
     * imported too, which is what actually trains the Elo model.
     */
    @PostMapping("/season")
    public SyncResultDto syncSeason(@RequestParam long tournamentId,
                                    @RequestParam(required = false) Long seasonId,
                                    @RequestParam(defaultValue = "true") boolean results) {
        return syncService.syncSeason(tournamentId, seasonId, results);
    }

    /** All competitions listed in app.sync.competitions, in one go. */
    @PostMapping("/competitions")
    public ResponseEntity<SyncResultDto> syncCompetitions(
            @RequestParam(defaultValue = "true") boolean results) {
        try {
            return ResponseEntity.ok(syncService.syncConfiguredCompetitions(results));
        } catch (ExternalDataException e) {
            // Bad competition id, disabled sync, unreachable provider: the message
            // is actionable, so a 400 says more than a stack trace would.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new SyncResultDto("sofascore", "configured competitions", null,
                            0, 0, 0, 0, 0, 0, false, e.getMessage()));
        }
    }
}
