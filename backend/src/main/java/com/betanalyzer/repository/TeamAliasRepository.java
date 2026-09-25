package com.betanalyzer.repository;

import com.betanalyzer.entity.TeamAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamAliasRepository extends JpaRepository<TeamAlias, Long> {
    Optional<TeamAlias> findByAliasNameIgnoreCase(String aliasName);
}