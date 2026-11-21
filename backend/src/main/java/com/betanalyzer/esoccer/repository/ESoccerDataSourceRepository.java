package com.betanalyzer.esoccer.repository;

import com.betanalyzer.esoccer.entity.ESoccerDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ESoccerDataSourceRepository extends JpaRepository<ESoccerDataSource, Long> {
    Optional<ESoccerDataSource> findByName(String name);
    List<ESoccerDataSource> findByEnabled(boolean enabled);
}
