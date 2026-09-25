export interface Match {
  id: number;
  homeTeam: string;
  awayTeam: string;
  league: string;
  kickOff: string;
  status: 'SCHEDULED' | 'PLAYED' | 'POSTPONED';
  homeGoals: number | null;
  awayGoals: number | null;
}
