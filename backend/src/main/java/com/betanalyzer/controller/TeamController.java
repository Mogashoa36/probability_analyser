package com.betanalyzer.controller;

import com.betanalyzer.dto.TeamDto;
import com.betanalyzer.entity.Team;
import com.betanalyzer.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Basic CRUD so team-strength data (Elo, form, goal averages) can be
 * viewed and maintained as more results come in.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;

    @GetMapping
    public List<TeamDto> all() {
        return teamRepository.findAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    public TeamDto create(@RequestBody TeamDto dto) {
        Team team = new Team(dto.getName(), dto.getLeague(), dto.getEloRating() > 0 ? dto.getEloRating() : 1500);
        team.setFormPoints(dto.getFormPoints());
        team.setGoalsForAvg(dto.getGoalsForAvg() > 0 ? dto.getGoalsForAvg() : 1.2);
        team.setGoalsAgainstAvg(dto.getGoalsAgainstAvg() > 0 ? dto.getGoalsAgainstAvg() : 1.2);
        return toDto(teamRepository.save(team));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        teamRepository.deleteById(id);
    }

    private TeamDto toDto(Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getLeague(), t.getEloRating(),
                t.getFormPoints(), t.getGoalsForAvg(), t.getGoalsAgainstAvg());
    }
}
