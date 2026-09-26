package com.betanalyzer.service;

import com.betanalyzer.config.SyncProperties;
import com.betanalyzer.dto.SyncResultDto;
import com.betanalyzer.dto.SyncStatusDto;
import com.betanalyzer.entity.Match;
import com.betanalyzer.entity.Team;
import com.betanalyzer.repository.MatchRepository;
import com.betanalyzer.repository.TeamRepository;
import com.betanalyzer.service.external.ExternalDataException;
import com.betanalyzer.service.external.ExternalDataProvider;
import com.betanalyzer.service.external.ExternalEvent;
import com.betanalyzer.service.external.ExternalTeamRef;
import com.betanalyzer.service.external.SofaScoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pulls real fixtures and results from an external provider and folds them
 * into the local database, so the model learns from real football instead of
 * only the sample rows in data.sql.
 *
 * <p>Two properties matter more than anything else here:
 *
 * <ol>
 *   <li><b>Idempotency.</b> A sync is safe to run repeatedly. Teams and
 *       fixtures are matched on their provider id first, then on name, so a
 *       second run updates rows instead of creating duplicates.</li>
 *   <li><b>Results applied exactly once.</b> Elo ratings and goal averages are
 *       updated only for a match that was not already {@code PLAYED} before this
 *       run. Without that guard, syncing the same results twice would corrupt
 *       every rating in the database.</li>
 * </ol>
 */
@Slf4j
@Service
public class SyncService {

    private final ExternalDataProvider provider;
    private final SofaScoreClient sofascore;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final TeamStrengthService teamStrengthService;
    private final SyncProperties properties;

    private volatile LocalDateTime lastRanAt;
    private volatile String lastMessage;
    private volatile boolean lastRunSucceeded;

    public SyncService(ExternalDataProvider provider,
                       SofaScoreClient sofascore,
                       TeamRepository teamRepository,
                       MatchRepository matchRepository,
                       TeamStrengthService teamStrengthService,
                       SyncProperties properties) {
        this.provider = provider;
        this.sofascore = sofascore;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.teamStrengthService = teamStrengthService;
        this.properties = properties;
    }

    public SyncStatusDto status() {
        return new SyncStatusDto(
                properties.isEnabled(),
                provider.name(),
                provider.describeEndpoint(),
                provider.isConfigured(),
                lastRanAt == null ? null : lastRanAt.toString(),
                lastMessage,
                lastRunSucceeded,
                teamRepository.countByExternalIdIsNotNull(),
                matchRepository.countByStatus(Match.MatchStatus.SCHEDULED),
                matchRepository.countByStatus(Match.MatchStatus.PLAYED),
                properties.getCompetitions());
    }

    /** Every fixture the provider has scheduled for one calendar day. */
    public SyncResultDto syncDay(LocalDate date) {
        requireEnabled();
        return run("fixtures for " + date, () -> provider.scheduledEvents(date));
    }

    /** One competition season - pass finishedOnly to feed the strength model. */
    public SyncResultDto syncSeason(long tournamentId, Long seasonId, boolean finishedOnly) {
        requireEnabled();
        if (seasonId == null) {
            throw new ExternalDataException("seasonId is required for a season sync.");
        }
        return run((finishedOnly ? "played matches" : "upcoming fixtures")
                        + " for competition " + tournamentId + " season " + seasonId,
                () -> provider.seasonEvents(tournamentId, seasonId, finishedOnly));
    }

    /**
     * Syncs every competition listed in app.sync.competitions, resolving each
     * one's current season. One call to seed the whole app from real data.
     */
    public SyncResultDto syncConfiguredCompetitions(boolean includeResults) {
        requireEnabled();
        List<SyncResultDto> parts = new ArrayList<>();
        for (String spec : properties.getCompetitions()) {
            SyncResultDto part = syncOneCompetition(spec, includeResults);
            if (part != null) parts.add(part);
        }
        return combine(provider.name(), "configured competitions (" + parts.size() + ")", parts);
    }

    private SyncResultDto syncOneCompetition(String spec, boolean includeResults) {
        long tournamentId;
        String label;
        try {
            int colon = spec.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("missing ':'");
            tournamentId = Long.parseLong(spec.substring(0, colon).trim());
            label = spec.substring(colon + 1).trim();
        } catch (RuntimeException e) {
            lastMessage = "Ignoring malformed competition entry \"" + spec
                    + "\" - expected \"id:Name\" in app.sync.competitions.";
            log.warn("Malformed competition entry: {}", spec);
            return null;
        }

        long seasonId = sofascore.currentSeasonId(tournamentId);
        List<SyncResultDto> parts = new ArrayList<>();
        parts.add(run("upcoming fixtures for " + label,
                () -> provider.seasonEvents(tournamentId, seasonId, false)));
        if (includeResults) {
            parts.add(run("played matches for " + label,
                    () -> provider.seasonEvents(tournamentId, seasonId, true)));
        }
        return combine(provider.name(), label, parts);
    }

    // ------------------------------------------------------------------ internals

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ExternalDataException("External data sync is disabled (app.sync.enabled=false).");
        }
    }

    private SyncResultDto run(String source, Supplier<List<ExternalEvent>> fetch) {
        Counters counters = new Counters();
        try {
            for (ExternalEvent event : fetch.get()) {
                if (!importEvent(event, counters)) counters.skipped++;
            }
            String message = "Imported " + counters.fixturesCreated + " new and updated "
                    + counters.fixturesUpdated + " existing fixtures, added " + counters.teamsCreated
                    + " teams, and recorded " + counters.resultsRecorded
                    + " results into the strength model.";
            SyncResultDto result = combine(provider.name(), source, message, counters, true);
            remember(result);
            return result;
        } catch (ExternalDataException e) {
            // Expected, user-fixable failures: surface the message, not a stack trace.
            lastRunSucceeded = false;
            lastMessage = e.getMessage();
            lastRanAt = LocalDateTime.now();
            log.warn("Sync failed for [{}]: {}", source, e.getMessage());
            return new SyncResultDto(provider.name(), source, lastRanAt.toString(),
                    0, 0, 0, 0, 0, 0, false, e.getMessage());
        } catch (RuntimeException e) {
            lastRunSucceeded = false;
            lastMessage = "Unexpected error while syncing " + source + ": " + e;
            lastRanAt = LocalDateTime.now();
            log.error("Unexpected sync failure for [{}]", source, e);
            return new SyncResultDto(provider.name(), source, lastRanAt.toString(),
                    0, 0, 0, 0, 0, 0, false, lastMessage);
        }
    }


    /** @return false when the event was unusable and had to be skipped. */
    private boolean importEvent(ExternalEvent event, Counters counters) {
        if (!event.isUsable()) return false;

        Team home = resolveTeam(event.homeTeam(), event.competition(), counters);
        Team away = resolveTeam(event.awayTeam(), event.competition(), counters);
        if (home == null || away == null || home.getId().equals(away.getId())) return false;

        LocalDateTime kickOff = LocalDateTime.ofInstant(event.kickOff(), zone());

        Optional<Match> existing = matchRepository.findByExternalId(event.externalId());
        if (existing.isEmpty()) {
            // Adopt a hand-entered fixture for the same two clubs on the same day,
            // otherwise a league we seeded by hand shows every match twice.
            existing = findMatchingFixture(home, away, kickOff);
        }

        boolean isNew = existing.isEmpty();
        Match match = existing.orElseGet(Match::new);
        match.setExternalId(event.externalId());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setLeague(event.competition());
        match.setKickOff(kickOff);

        if (event.finished() && event.homeGoals() != null && event.awayGoals() != null) {
            // The guard that keeps ratings honest: a match already marked PLAYED
            // has been through recordResult() before, so leave it alone.
            if (match.getStatus() != Match.MatchStatus.PLAYED) {
                match.setHomeGoals(event.homeGoals());
                match.setAwayGoals(event.awayGoals());
                match.setStatus(Match.MatchStatus.PLAYED);
                matchRepository.save(match);
                teamStrengthService.recordResult(match);
                counters.resultsRecorded++;
            }
        } else if (event.cancelled()) {
            // Called off at the provider: keep it out of the upcoming list, and
            // never let it count as a result.
            if (match.getStatus() != Match.MatchStatus.PLAYED) {
                match.setStatus(Match.MatchStatus.POSTPONED);
            }
        } else if (match.getStatus() != Match.MatchStatus.PLAYED) {
            match.setStatus(Match.MatchStatus.SCHEDULED);
        }

        matchRepository.save(match);
        if (isNew) {
            counters.fixturesCreated++;
        } else {
            counters.fixturesUpdated++;
        }
        return true;
    }

    /**
     * Finds a club by provider id, then by name, so an existing hand-entered
     * team is adopted and linked rather than duplicated under the provider's
     * slightly different spelling of the name.
     */
    private Team resolveTeam(ExternalTeamRef ref, String league, Counters counters) {
        Optional<Team> byExternal = teamRepository.findByExternalId(ref.externalId());
        if (byExternal.isPresent()) {
            Team t = byExternal.get();
            if (applyTeamDetails(t, ref, league)) {
                teamRepository.save(t);
                counters.teamsUpdated++;
            }
            return t;
        }

        Optional<Team> byName = ref.name() == null
                ? Optional.empty()
                : teamRepository.findByNameIgnoreCase(ref.name().trim());

        Team team = byName.orElseGet(() -> new Team(ref.name(), league, 1500));
        boolean isNew = byName.isEmpty();
        team.setExternalId(ref.externalId());
        applyTeamDetails(team, ref, league);
        teamRepository.save(team);
        if (isNew) {
            counters.teamsCreated++;
        } else {
            counters.teamsUpdated++;
        }
        return team;
    }

    private boolean applyTeamDetails(Team team, ExternalTeamRef ref, String league) {
        boolean changed = false;
        if (ref.name() != null && !ref.name().isBlank() && !ref.name().equals(team.getName())) {
            team.setName(ref.name().trim());
            changed = true;
        }
        if (ref.logoUrl() != null && !ref.logoUrl().equals(team.getLogoUrl())) {
            team.setLogoUrl(ref.logoUrl());
            changed = true;
        }
        if ((team.getLeague() == null || team.getLeague().isBlank()) && league != null) {
            team.setLeague(league);
            changed = true;
        }
        return changed;
    }


    private Optional<Match> findMatchingFixture(Team home, Team away, LocalDateTime kickOff) {
        return matchRepository
                .findByHomeTeamNameIgnoreCaseAndAwayTeamNameIgnoreCase(home.getName(), away.getName())
                .stream()
                .filter(m -> m.getKickOff() != null
                        && m.getKickOff().toLocalDate().equals(kickOff.toLocalDate()))
                .findFirst();
    }

    private ZoneId zone() {
        String configured = properties.getZone();
        if (configured == null || configured.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(configured);
        } catch (RuntimeException e) {
            log.warn("Ignoring invalid app.sync.zone=\"{}\", using the system zone", configured);
            return ZoneId.systemDefault();
        }
    }

    private void remember(SyncResultDto result) {
        this.lastRanAt = LocalDateTime.now();
        this.lastMessage = result.getMessage();
        this.lastRunSucceeded = result.isSuccess();
    }

    private SyncResultDto combine(String providerName, String source, List<SyncResultDto> parts) {
        Counters total = new Counters();
        StringBuilder messages = new StringBuilder();
        boolean success = false;
        for (SyncResultDto p : parts) {
            total.add(p);
            success |= p.isSuccess();
            if (p.getMessage() != null && !p.getMessage().isBlank()) {
                if (messages.length() > 0) messages.append(' ');
                messages.append(p.getMessage());
            }
        }
        return combine(providerName, source, messages.toString(), total, success);
    }

    private SyncResultDto combine(String providerName, String source, String message,
                                  Counters c, boolean success) {
        return new SyncResultDto(providerName, source, LocalDateTime.now().toString(),
                c.teamsCreated, c.teamsUpdated, c.fixturesCreated, c.fixturesUpdated,
                c.resultsRecorded, c.skipped, success, message);
    }

    /** Mutable tally for one import run. */
    private static final class Counters {
        int teamsCreated;
        int teamsUpdated;
        int fixturesCreated;
        int fixturesUpdated;
        int resultsRecorded;
        int skipped;

        void add(SyncResultDto other) {
            teamsCreated += other.getTeamsCreated();
            teamsUpdated += other.getTeamsUpdated();
            fixturesCreated += other.getFixturesCreated();
            fixturesUpdated += other.getFixturesUpdated();
            resultsRecorded += other.getResultsRecorded();
            skipped += other.getSkipped();
        }
    }
}

