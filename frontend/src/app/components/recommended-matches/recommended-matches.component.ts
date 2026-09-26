import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RecommendationService } from '../../services/recommendation.service';
import { Recommendation } from '../../models/recommendation.model';

type ConfidenceFilter = 'ALL' | 'STRONG' | 'MODERATE' | 'SPECULATIVE';
type MarketGroupFilter = 'ALL' | 'RESULT' | 'GOALS';
type WindowFilter = 'ALL' | 'TODAY' | 'NEXT_3' | 'NEXT_7';
type SortKey = 'CONFIDENCE' | 'KICKOFF' | 'PROBABILITY';

@Component({
  selector: 'app-recommended-matches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recommended-matches.component.html',
  styleUrl: './recommended-matches.component.css',
})
export class RecommendedMatchesComponent implements OnInit {
  mode: 'stored' | 'paste' = 'stored';
  rawText = '';
  loading = false;
  error: string | null = null;
  recommendations: Recommendation[] = [];

  /** How many fixtures the backend should rank. Filtering is client-side, so a
   *  larger pool makes the filters more useful. */
  limit = 25;
  readonly limitOptions = [10, 25, 50, 100];

  // --- filter state, all applied client-side by the `visible` getter ---
  search = '';
  confidence: ConfidenceFilter = 'ALL';
  marketGroup: MarketGroupFilter = 'ALL';
  league = 'ALL';
  window: WindowFilter = 'ALL';
  minProbability = 0;
  sort: SortKey = 'CONFIDENCE';

  constructor(private recommendationService: RecommendationService) {}

  ngOnInit(): void {
    this.loadStored();
  }

  setMode(mode: 'stored' | 'paste'): void {
    this.mode = mode;
    this.error = null;
    // Kick-off times only exist for stored fixtures, so a date-window filter
    // chosen in the other mode carries over as a no-op rather than a surprise.
    if (mode === 'stored') this.loadStored();
  }

  loadStored(): void {
    this.loading = true;
    this.recommendationService.getStoredRecommendations(this.limit).subscribe({
      next: (res) => {
        this.recommendations = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the recommendation service. Is the backend running on :8081?';
        this.loading = false;
      },
    });
  }

  analyzePasted(): void {
    if (!this.rawText.trim()) {
      this.error = 'Paste at least one fixture first, e.g. "Team A vs Team B".';
      return;
    }
    this.loading = true;
    this.error = null;
    this.recommendationService.getRecommendationsFromList(this.rawText, this.limit).subscribe({
      next: (res) => {
        this.recommendations = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the recommendation service. Is the backend running on :8081?';
        this.loading = false;
      },
    });
  }

  /** Re-rank with the new fixture count whenever the limit changes. */
  onLimitChange(): void {
    if (this.mode === 'stored') {
      this.loadStored();
    } else if (this.rawText.trim()) {
      this.analyzePasted();
    }
  }

  // ------------------------------------------------------------------ filtering

  /** Every league present in the current result set, for the league dropdown. */
  get leagues(): string[] {
    const found = this.recommendations
      .map((r) => r.league)
      .filter((l): l is string => !!l);
    return Array.from(new Set(found)).sort((a, b) => a.localeCompare(b));
  }

  /** True when anything beyond the default sort is narrowing the list. */
  get hasActiveFilters(): boolean {
    return (
      this.search.trim() !== '' ||
      this.confidence !== 'ALL' ||
      this.marketGroup !== 'ALL' ||
      this.league !== 'ALL' ||
      this.window !== 'ALL' ||
      this.minProbability > 0
    );
  }

  resetFilters(): void {
    this.search = '';
    this.confidence = 'ALL';
    this.marketGroup = 'ALL';
    this.league = 'ALL';
    this.window = 'ALL';
    this.minProbability = 0;
    this.sort = 'CONFIDENCE';
  }

  /** The recommendations actually shown, after filtering and sorting. */
  get visible(): Recommendation[] {
    const term = this.search.trim().toLowerCase();
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();

    const filtered = this.recommendations.filter((rec) => {
      if (term) {
        const haystack = `${rec.homeTeam} ${rec.awayTeam} ${rec.league ?? ''}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }

      if (this.confidence !== 'ALL' && this.confidenceLabel(rec.confidenceScore) !== this.confidence) {
        return false;
      }

      if (this.marketGroup !== 'ALL' && this.marketGroupOf(rec.recommendedMarket) !== this.marketGroup) {
        return false;
      }

      if (this.league !== 'ALL' && rec.league !== this.league) return false;

      if (this.minProbability > 0 && rec.modelProbabilityPct < this.minProbability) return false;

      if (this.window !== 'ALL') {
        const kickOff = this.kickOffTime(rec);
        // A pasted fixture carries no kick-off, so it can only show under "Any time".
        if (kickOff === null || kickOff < startOfToday) return false;
        const span = this.window === 'TODAY' ? 1 : this.window === 'NEXT_3' ? 3 : 7;
        if (kickOff >= startOfToday + span * 86400000) return false;
      }

      return true;
    });

    return filtered.sort((a, b) => {
      switch (this.sort) {
        case 'PROBABILITY':
          return b.modelProbabilityPct - a.modelProbabilityPct;
        case 'KICKOFF': {
          const ka = this.kickOffTime(a);
          const kb = this.kickOffTime(b);
          // Fixtures with no kick-off always sink to the bottom.
          if (ka === null && kb === null) return b.confidenceScore - a.confidenceScore;
          if (ka === null) return 1;
          if (kb === null) return -1;
          return ka - kb;
        }
        default:
          return b.confidenceScore - a.confidenceScore;
      }
    });
  }

  // ------------------------------------------------------------------ formatting

  /** Kick-off as epoch millis, or null when the fixture has no usable time. */
  kickOffTime(rec: Recommendation): number | null {
    if (!rec.kickOff) return null;
    const parsed = new Date(rec.kickOff);
    return isNaN(parsed.getTime()) ? null : parsed.getTime();
  }

  /** Short "when is it" label: TODAY / TOMORROW / IN 5 DAYS / KICKED OFF / TIME TBC. */
  kickOffBadge(rec: Recommendation): string {
    const kickOff = this.kickOffTime(rec);
    if (kickOff === null) return 'TIME TBC';

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const days = Math.floor((kickOff - startOfToday) / 86400000);

    if (days < 0) return 'KICKED OFF';
    if (days === 0) return 'TODAY';
    if (days === 1) return 'TOMORROW';
    if (days <= 7) return `IN ${days} DAYS`;
    return '';
  }

  /** True for a match kicking off within 48 hours - used to highlight the card. */
  isImminent(rec: Recommendation): boolean {
    const kickOff = this.kickOffTime(rec);
    if (kickOff === null) return false;
    const diff = kickOff - Date.now();
    return diff > -3600000 && diff < 2 * 86400000;
  }

  private marketGroupOf(market: string): 'RESULT' | 'GOALS' {
    const upper = market.toUpperCase();
    if (upper.includes('OVER') || upper.includes('UNDER') || upper.includes('BTTS')) return 'GOALS';
    return 'RESULT';
  }

  confidenceLabel(score: number): string {
    if (score >= 70) return 'STRONG';
    if (score >= 50) return 'MODERATE';
    return 'SPECULATIVE';
  }

  confidenceClass(score: number): string {
    if (score >= 70) return 'conf-strong';
    if (score >= 50) return 'conf-moderate';
    return 'conf-speculative';
  }

  marketLabel(market: string): string {
    return market.replace(/_/g, ' ');
  }
}
