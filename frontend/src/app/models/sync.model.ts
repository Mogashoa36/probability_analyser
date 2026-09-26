/** GET /api/sync/status - what the provider connection looks like right now. */
export interface SyncStatus {
  enabled: boolean;
  provider: string;
  endpoint: string;
  configured: boolean;
  lastRanAt: string | null;
  lastMessage: string | null;
  lastRunSucceeded: boolean;
  teamsFromProvider: number;
  scheduledFixtures: number;
  playedFixtures: number;
  competitions: string[];
}

/** Result of one sync call - returned by every POST under /api/sync. */
export interface SyncResult {
  provider: string;
  source: string;
  ranAt: string | null;
  teamsCreated: number;
  teamsUpdated: number;
  fixturesCreated: number;
  fixturesUpdated: number;
  resultsRecorded: number;
  skipped: number;
  success: boolean;
  message: string | null;
}
