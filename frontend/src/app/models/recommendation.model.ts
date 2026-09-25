export interface Recommendation {
  matchId: number | null;
  homeTeam: string;
  awayTeam: string;
  league: string | null;
  recommendedMarket: string;
  modelProbabilityPct: number;
  confidenceScore: number;
  reasoning: string;
}
