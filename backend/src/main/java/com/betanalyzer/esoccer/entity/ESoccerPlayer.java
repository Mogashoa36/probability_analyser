package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An esports player / gamer. Distinct from the football club they select in-game.
 */
@Entity
@Table(name = "esoccer_player", uniqueConstraints = {
        @UniqueConstraint(columnNames = "gamer_id"),
        @UniqueConstraint(columnNames = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gamer_id", nullable = false)
    private String gamerId;

    private String realName;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "image_url")
    private String imageUrl;

    private String country;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ESoccerPlayer(String gamerId, String externalId) {
        this.gamerId = gamerId;
        this.externalId = externalId;
    }
}
