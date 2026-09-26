package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Current state of the external-data connection, so the sync page can show what
 * is configured and what the last run did without the user having to run it.
 */
@Getter
@AllArgsConstructor
public class SyncStatusDto {
    private boolean enabled;
    private String provider;
    private String endpoint;
    private boolean configured;
    private String lastRanAt;        // null if it has never run
    private String lastMessage;      // null until the first run
    private boolean lastRunSucceeded;
    private long teamsFromProvider;
    private long scheduledFixtures;
    private long playedFixtures;
    private List<String> competitions;
}
