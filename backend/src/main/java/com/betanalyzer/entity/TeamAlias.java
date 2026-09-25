package com.betanalyzer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A nickname or shorthand for a team, e.g. "Man City" -> Manchester City.
 *
 * Bookmakers and pasted bet slips almost never use a club's official name, so
 * without this the parser would treat "Barca" as an unknown side and fall back
 * to league-average figures, flagging the leg as low confidence.
 */
@Entity
@Table(name = "team_alias")
@Getter
@Setter
@NoArgsConstructor
public class TeamAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "alias_name", nullable = false, unique = true)
    private String aliasName;
}