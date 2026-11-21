package com.betanalyzer.esoccer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "esoccer_data_source")
@Getter
@Setter
@NoArgsConstructor
public class ESoccerDataSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String type;
    private LocalDateTime lastSyncAt;
    private boolean enabled = true;
    @Column(length = 2000)
    private String statusMessage;
    private int recordCount = 0;
    public ESoccerDataSource(String name, String type) {
        this.name = name;
        this.type = type;
    }
}
