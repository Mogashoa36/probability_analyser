package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "esoccer_team", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name"),
        @UniqueConstraint(columnNames = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ESoccerTeam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(name = "external_id")
    private String externalId;
    @Column(name = "logo_url")
    private String logoUrl;
    public ESoccerTeam(String name, String externalId) {
        this.name = name;
        this.externalId = externalId;
    }
}
