package com.betanalyzer.controller;

import com.betanalyzer.dto.MatchDto;
import com.betanalyzer.entity.Match;
import com.betanalyzer.repository.MatchRepository;
import com.betanalyzer.service.TeamStrengthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manage fixtures: add upcoming matches (feeds the Recommendations page)
 * and record results (feeds the strength model via TeamStrengthService).
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchRepository matchRepository;
    private final TeamStrengthService teamStrengthService;

    @GetMapping
    public List<MatchDto> all() {
        return matchRepository.findAll().stream().map(this::toDto).toList();
    }

    @PostMapping("/{id}/result")
    public MatchDto recordResult(@PathVariable Long id, @RequestParam int homeGoals, @RequestParam int awayGoals) {
        Match match = matchRepository.findById(id).orElseThrow();
        match.setHomeGoals(homeGoals);
        match.setAwayGoals(awayGoals);
        match.setStatus(Match.MatchStatus.PLAYED);
        teamStrengthService.recordResult(match);
        return toDto(matchRepository.save(match));
    }

    private MatchDto toDto(Match m) {
        return new MatchDto(m.getId(), m.getHomeTeam().getName(), m.getAwayTeam().getName(),
                m.getLeague(), m.getKickOff(), m.getStatus().name(), m.getHomeGoals(), m.getAwayGoals());
    }
}
