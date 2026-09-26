export interface Recommendation {
  matchId: number | null;
  homeTeam: string;
  awayTeam: string;
  league: string | null;
  /**
   * Kick-off date/time as an ISO-8601 local string, e.g. "2026-10-04T15:30:00".
   * Null for fixtures that came from a pasted list, which carries no kick-off time.
   */
  kickOff: string | null;
  recommendedMarket: string;
  modelProbabilityPct: number;
  confidenceScore: number;
  reasoning: string;
}
