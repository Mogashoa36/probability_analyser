package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.dto.CreateMatchRequest;
import com.betanalyzer.esoccer.dto.MatchSummaryDto;
import com.betanalyzer.esoccer.entity.ESoccerCompetition;
import com.betanalyzer.esoccer.entity.ESoccerGameMode;
import com.betanalyzer.esoccer.entity.ESoccerMatch;
import com.betanalyzer.esoccer.entity.ESoccerPlayer;
import com.betanalyzer.esoccer.entity.ESoccerTeam;
import com.betanalyzer.esoccer.service.ESoccerPredictionService;
import com.betanalyzer.esoccer.repository.ESoccerCompetitionRepository;
import com.betanalyzer.esoccer.repository.ESoccerGameModeRepository;
import com.betanalyzer.esoccer.repository.ESoccerMatchRepository;
import com.betanalyzer.esoccer.repository.ESoccerPlayerRepository;
import com.betanalyzer.esoccer.repository.ESoccerTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/esoccer/matches")
@RequiredArgsConstructor
public class ESoccerMatchController {

    private final ESoccerMatchRepository matchRepository;
    private final ESoccerPlayerRepository playerRepository;
    private final ESoccerTeamRepository teamRepository;
    private final ESoccerCompetitionRepository competitionRepository;
    private final ESoccerGameModeRepository gameModeRepository;
    private final ESoccerPredictionService predictionService;

    @GetMapping
    public List<MatchSummaryDto> all() {
        return predictionService.listAllMatches();
    }

    @GetMapping("/upcoming")
    public List<MatchSummaryDto> upcoming() {
        return predictionService.listUpcoming();
    }

    @PostMapping
    public MatchSummaryDto create(@RequestBody CreateMatchRequest request) {
        var match = predictionService.createMatch(request);
        return toDto(match);
    }

    @PostMapping("/{id}/result")
    public MatchSummaryDto recordResult(@PathVariable Long id,
                                        @RequestParam Integer goalsA,
                                        @RequestParam Integer goalsB) {
        predictionService.recordMatchResult(id, goalsA, goalsB);
        return toDto(matchRepository.findById(id).orElseThrow());
    }

    // ---- players ----
    @GetMapping("/players")
    public List<String> listPlayers() {
        return playerRepository.findAll().stream()
                .map(ESoccerPlayer::getGamerId).collect(Collectors.toList());
    }

    // ---- competitions ----
    @GetMapping("/competitions")
    public List<ESoccerCompetition> listCompetitions() {
        return competitionRepository.findAll();
    }

    @PostMapping("/competitions")
    public ESoccerCompetition createCompetition(@RequestBody ESoccerCompetition comp) {
        return competitionRepository.save(comp);
    }

    // ---- game modes ----
    @GetMapping("/game-modes")
    public List<ESoccerGameMode> listGameModes() {
        return gameModeRepository.findAll();
    }

    @PostMapping("/game-modes")
    public ESoccerGameMode createGameMode(@RequestBody ESoccerGameMode mode) {
        return gameModeRepository.save(mode);
    }

    private MatchSummaryDto toDto(ESoccerMatch m) {
        return new MatchSummaryDto(
                m.getId(), m.getPlayerA().getGamerId(), m.getPlayerB().getGamerId(),
                m.getTeamA().getName(), m.getTeamB().getName(),
                m.getCompetition().getName(), m.getGameMode().getName(),
                m.getMatchDateTime(), m.getStatus().name(), m.getGoalsA(), m.getGoalsB());
    }
}
