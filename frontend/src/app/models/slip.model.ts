export type Market =
  | 'HOME_WIN'
  | 'AWAY_WIN'
  | 'DRAW'
  | 'OVER_2_5'
  | 'UNDER_2_5'
  | 'BTTS_YES'
  | 'BTTS_NO'
  | 'UNKNOWN';

export type Confidence = 'LOW' | 'MEDIUM' | 'HIGH';
export type RiskRating = 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY HIGH';

/** One structured leg, used when the user builds a slip via form fields
 *  instead of pasting free text. */
export interface SelectionInput {
  homeTeam: string;
  awayTeam: string;
  market: Market;
  odds: number;
}

/** Request body for POST /api/slips/analyze. Supply rawText OR selections. */
export interface SlipAnalysisRequest {
  rawText?: string;
  selections?: SelectionInput[];
}

export interface SelectionResult {
  homeTeam: string;
  awayTeam: string;
  market: string;
  odds: number;
  modelProbabilityPct: number;
  impliedProbabilityPct: number;
  edgePct: number;
  confidence: Confidence;
  note: string;
}

export interface SlipAnalysisResponse {
  selections: SelectionResult[];
  totalOdds: number;
  combinedWinProbabilityPct: number;
  impliedProbabilityPct: number;
  overallRiskRating: RiskRating;
  refinedSuggestions: string[];
  weakestLegs: SelectionResult[];
}
