package com.betanalyzer.service.external;

import com.betanalyzer.config.SyncProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads fixtures and results from SofaScore.
 *
 * <p>Two access routes are supported, selected purely by configuration:
 * <ul>
 *   <li><b>Official API</b> - {@code https://api.sofascore.com/api/v1} with an
 *       API key. The supported route, and the one to use in production.</li>
 *   <li><b>Public web endpoint</b> - {@code https://www.sofascore.com/api/v1},
 *       no key. It sits behind bot protection and answers HTTP 403 to
 *       datacentre IPs and CI runners. Usable from a home connection, but it
 *       is an undocumented interface - don't build on it.</li>
 * </ul>
 *
 * <p>Both return the same JSON shape, so the mapping below works either way. A
 * 403 becomes an explicit "blocked" message rather than a stack trace, because
 * it is the most likely failure and it is not a bug.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreClient implements ExternalDataProvider {

    private final RestClient restClient;
    private final SyncProperties properties;

    private static final ParameterizedTypeReference<EventsResponse> EVENTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SeasonsResponse> SEASONS =
            new ParameterizedTypeReference<>() {};

    @Override
    public String name() {
        return "sofascore";
    }

    @Override
    public String describeEndpoint() {
        String configuredKey = properties.getSofascore().getApiKey();
        String key = (configuredKey == null || configuredKey.isBlank()) ? "no API key" : "API key set";
        return properties.getSofascore().getBaseUrl() + "  (" + key + ")";
    }

    @Override
    public boolean isConfigured() {
        String base = properties.getSofascore().getBaseUrl();
        return base != null && !base.isBlank();
    }

    @Override
    public List<ExternalEvent> scheduledEvents(LocalDate date) {
        return map(get("/sport/football/scheduled-events/" + date,
                "the fixture list for " + date, EVENTS));
    }

    @Override
    public List<ExternalEvent> seasonEvents(long tournamentId, long seasonId, boolean finishedOnly) {
        // "last" is the played history, "next" the upcoming schedule.
        String path = "/unique-tournament/" + tournamentId + "/season/" + seasonId
                + (finishedOnly ? "/events/last/0" : "/events/next/0");
        return map(get(path, (finishedOnly ? "played " : "upcoming ")
                + "matches for competition " + tournamentId + " season " + seasonId, EVENTS));
    }

    /** Current season id for a competition - turns "Premier League" into a season. */
    public long currentSeasonId(long tournamentId) {
        SeasonsResponse response = get("/unique-tournament/" + tournamentId + "/seasons",
                "seasons for competition " + tournamentId, SEASONS);
        List<Season> seasons = response.seasons() == null ? List.of() : response.seasons();
        if (seasons.isEmpty()) {
            throw new ExternalDataException("SofaScore listed no seasons for competition "
                    + tournamentId + " - check the competition id.");
        }
        Season newest = seasons.stream()
                .max((a, b) -> Integer.compare(a.year() == null ? 0 : a.year(),
                                               b.year() == null ? 0 : b.year()))
                .orElseThrow();
        return newest.id();
    }

    /**
     * A GET whose JSON is decoded into {@code type}. RestClient needs a
     * ParameterizedTypeReference rather than a Class here, because a class
     * literal for a type variable (T.class) is not legal Java.
     */
    private <T> T get(String path, String what, ParameterizedTypeReference<T> type) {
        try {
            return restClient.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            throw new ExternalDataException(describe(what, e), e);
        }
    }

    /** Turns a transport failure into something the user can act on. */
    private String describe(String what, RestClientException e) {
        int status = statusOf(e);
        return switch (status) {
            case 401 -> "SofaScore rejected the API key. Set app.sync.sofascore.api-key in application.properties.";
            case 403 -> "SofaScore blocked the request (HTTP 403). The public endpoint rejects non-browser "
                    + "callers; add an official API key, or run this from a home connection.";
            case 404 -> "SofaScore has no " + what + " (HTTP 404). Check the competition/season id.";
            case 429 -> "SofaScore is rate-limiting this client (HTTP 429). Wait a minute and retry.";
            default -> {
                if (status == 0) {
                    yield "Could not reach SofaScore for " + what + ": " + rootMessage(e)
                            + ". Check your network and app.sync.sofascore.base-url.";
                }
                yield "SofaScore returned HTTP " + status + " for " + what + ".";
            }
        };
    }

    /**
     * The HTTP status, or 0 when the request never got a response (DNS
     * failure, connection refused, timeout). Only the response-carrying
     * exception type exposes it.
     */
    private int statusOf(RestClientException e) {
        if (e instanceof RestClientResponseException response && response.getStatusCode() != null) {
            return response.getStatusCode().value();
        }
        return 0;
    }

    private String rootMessage(Throwable t) {
        Throwable cursor = t;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    // ------------------------------------------------------------------ mapping

    private List<ExternalEvent> map(EventsResponse response) {
        if (response == null || response.events() == null) return List.of();
        List<ExternalEvent> out = new ArrayList<>();
        for (Event e : response.events()) {
            ExternalEvent mapped = mapEvent(e);
            if (mapped != null && mapped.isUsable()) out.add(mapped);
        }
        return out;
    }

    private ExternalEvent mapEvent(Event e) {
        if (e == null || e.id() == null || e.startTimestamp() == null) return null;

        String competition = (e.tournament() == null || e.tournament().name() == null)
                ? properties.getSofascore().getDefaultLeague()
                : e.tournament().name();

        Integer homeGoals = regulationScore(e.homeScore());
        Integer awayGoals = regulationScore(e.awayScore());
        // A "finished" event with no score is cancelled or postponed, not a 0-0.
        boolean finished = e.status() != null && Boolean.TRUE.equals(e.status().finished())
                && homeGoals != null && awayGoals != null;
        boolean cancelled = e.status() != null && (Boolean.TRUE.equals(e.status().cancelled())
                || "canceled".equalsIgnoreCase(e.status().type())
                || "postponed".equalsIgnoreCase(e.status().type()));

        return new ExternalEvent(
                name() + ":" + e.id(),
                competition,
                mapTeam(e.homeTeam()),
                mapTeam(e.awayTeam()),
                Instant.ofEpochSecond(e.startTimestamp()),
                homeGoals,
                awayGoals,
                e.status() == null ? "unknown" : e.status().type(),
                finished,
                cancelled);
    }

    /**
     * Regulation score where the provider gives one. Knockout ties can be
     * settled in extra time or on penalties, and feeding a shootout score into a
     * goals-based Poisson model would badly skew the goal averages.
     */
    private Integer regulationScore(Score s) {
        if (s == null) return null;
        return s.normaltime() != null ? s.normaltime() : s.current();
    }

    private ExternalTeamRef mapTeam(TeamRef t) {
        if (t == null || t.id() == null) return null;
        return new ExternalTeamRef(name() + ":" + t.id(), t.name(), t.logo());
    }


    // ------------------------------------------------------- provider JSON shapes
    // Only the fields this app needs; everything else SofaScore sends is ignored.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventsResponse(List<Event> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeasonsResponse(List<Season> seasons) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Season(Long id, Integer year, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(
            Long id,
            @JsonProperty("startTimestamp") Long startTimestamp,
            Tournament tournament,
            Status status,
            @JsonProperty("homeTeam") TeamRef homeTeam,
            @JsonProperty("awayTeam") TeamRef awayTeam,
            @JsonProperty("homeScore") Score homeScore,
            @JsonProperty("awayScore") Score awayScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tournament(Long id, String name, UniqueTournament uniqueTournament) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UniqueTournament(Long id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Status(String type, String description, Boolean finished, Boolean cancelled, String scoreStr) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TeamRef(Long id, String name, String slug, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Score(Integer current, Integer display, Integer normaltime, Integer penalty, Integer overtime) {}
}

