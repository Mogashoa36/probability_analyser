package com.betanalyzer.repository;

import com.betanalyzer.entity.BettingSlip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BettingSlipRepository extends JpaRepository<BettingSlip, Long> {
}
