package com.betanalyzer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A full multi-leg slip (Betway/Hollywoodbets style) submitted for analysis.
 */
@Entity
@Table(name = "betting_slip")
@Getter
@Setter
@NoArgsConstructor
public class BettingSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Product of each selection's bookmaker odds. */
    private double totalOdds;

    /** Product of each selection's model probability - the slip's true win chance. */
    private double combinedProbability;

    /** Implied probability from totalOdds (1/totalOdds). */
    private double impliedProbability;

    @OneToMany(mappedBy = "slip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlipSelection> selections = new ArrayList<>();
}
