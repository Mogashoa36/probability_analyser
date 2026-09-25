package com.betanalyzer.repository;

import com.betanalyzer.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByStatus(Match.MatchStatus status);
}
