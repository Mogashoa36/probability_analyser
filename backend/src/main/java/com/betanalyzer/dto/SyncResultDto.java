package com.betanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** What one sync run did. Returned to the UI straight after the call. */
@Getter
@AllArgsConstructor
public class SyncResultDto {
    private String provider;
    private String source;          // human description of what was pulled
    private String ranAt;           // ISO local timestamp
    private int teamsCreated;
    private int teamsUpdated;
    private int fixturesCreated;
    private int fixturesUpdated;
    private int resultsRecorded;    // finished matches fed into the strength model
    private int skipped;            // events the provider sent but we could not use
    private boolean success;
    private String message;
}
