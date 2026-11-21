package com.betanalyzer.esoccer.service;

import com.betanalyzer.esoccer.dto.CreateMatchRequest;
import com.betanalyzer.esoccer.entity.*;
import com.betanalyzer.esoccer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports historical eSoccer matches from CSV or JSON, and supports manual entry.
 * Validates input and deduplicates on provider externalId.
 */
@Slf4j
@Service
public class ESoccerImportService {

    private final ESoccerPlayerRepository playerRepository;
    private final ESoccerTeamRepository teamRepository;
    private final ESoccerCompetitionRepository competitionRepository;
    private final ESoccerGameModeRepository gameModeRepository;
    private final ESoccerMatchRepository matchRepository;
    private final ESoccerDataSourceRepository dataSourceRepository;
    private final ObjectMapper objectMapper;

    public ESoccerImportService(ESoccerPlayerRepository playerRepository,
                                ESoccerTeamRepository teamRepository,
                                ESoccerCompetitionRepository competitionRepository,
                                ESoccerGameModeRepository gameModeRepository,
                                ESoccerMatchRepository matchRepository,
                                ESoccerDataSourceRepository dataSourceRepository,
                                ObjectMapper objectMapper) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.competitionRepository = competitionRepository;
        this.gameModeRepository = gameModeRepository;
        this.matchRepository = matchRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.objectMapper = objectMapper;
    }

    /** Manual entry of a single upcoming match. */
    @Transactional
    public ESoccerMatch createMatch(CreateMatchRequest request) {
        ESoccerPlayer pA = resolveOrCreatePlayer(request.playerAGamerId(), null);
        ESoccerPlayer pB = resolveOrCreatePlayer(request.playerBGamerId(), null);
        ESoccerTeam tA = resolveOrCreateTeam(request.teamAName(), null);
        ESoccerTeam tB = resolveOrCreateTeam(request.teamBName(), null);
        ESoccerCompetition comp = resolveOrCreateCompetition(request.competitionName(), null, true);
        ESoccerGameMode mode = resolveOrCreateGameMode(request.gameModeName(), null,
                request.durationMinutes(), comp.isAllowDraws());

        if (request.externalId() != null && matchRepository.existsByExternalId(request.externalId())) {
            throw new IllegalArgumentException("Match with externalId " + request.externalId() + " already exists");
        }

        ESoccerMatch match = new ESoccerMatch(pA, pB, tA, tB, comp, mode,
                request.matchDateTime(), request.externalId());
        match.setDurationMinutes(request.durationMinutes());
        match.setSource(ESoccerMatch.DataSource.MANUAL);
        return matchRepository.save(match);
    }

    /** Import matches from CSV. Expected header:
     playerA,playerB,teamA,teamB,competition,gameMode,matchDateTime,durationMinutes,externalId
     */
    @Transactional
    public int importCsv(MultipartFile file) throws Exception {
        List<ESoccerMatch> imported = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header
            if (header == null) return 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 8) continue;

                CreateMatchRequest req = new CreateMatchRequest(
                        parts[0].trim(), parts[1].trim(),
                        parts[2].trim(), parts[3].trim(),
                        parts[4].trim(), parts[5].trim(),
                        LocalDateTime.parse(parts[6].trim(), DateTimeFormatter.ISO_DATE_TIME),
                        parts[7].trim().isEmpty() ? null : Integer.parseInt(parts[7].trim()),
                        parts.length > 8 ? parts[8].trim() : null
                );
                imported.add(createMatch(req));
            }
        }

        recordDataSource("CSV Import", "CSV", imported.size());
        return imported.size();
    }

    /** Import matches from JSON array. */
    @Transactional
    public int importJson(MultipartFile file) throws Exception {
        CreateMatchRequest[] requests = objectMapper.readValue(
                file.getBytes(), CreateMatchRequest[].class);
        int count = 0;
        for (CreateMatchRequest req : requests) {
            createMatch(req);
            count++;
        }
        recordDataSource("JSON Import", "JSON", count);
        return count;
    }

    // ---------------------------------------------------------------

    private ESoccerPlayer resolveOrCreatePlayer(String gamerId, String externalId) {
        return playerRepository.findByGamerIdIgnoreCase(gamerId)
                .orElseGet(() -> playerRepository.save(new ESoccerPlayer(gamerId, externalId)));
    }

    private ESoccerTeam resolveOrCreateTeam(String name, String externalId) {
        return teamRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> teamRepository.save(new ESoccerTeam(name, externalId)));
    }

    private ESoccerCompetition resolveOrCreateCompetition(String name, String externalId, boolean allowDraws) {
        return competitionRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> competitionRepository.save(new ESoccerCompetition(name, externalId, allowDraws)));
    }

    private ESoccerGameMode resolveOrCreateGameMode(String name, String externalId,
                                                    Integer durationMinutes, boolean allowDraws) {
        return gameModeRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    ESoccerGameMode mode = new ESoccerGameMode(name, externalId, durationMinutes, allowDraws);
                    return gameModeRepository.save(mode);
                });
    }

    private void recordDataSource(String name, String type, int count) {
        ESoccerDataSource source = dataSourceRepository.findByName(name)
                .orElseGet(() -> new ESoccerDataSource(name, type));
        source.setLastSyncAt(LocalDateTime.now());
        source.setRecordCount(count);
        source.setStatusMessage("Imported " + count + " records successfully");
        dataSourceRepository.save(source);
    }
}
