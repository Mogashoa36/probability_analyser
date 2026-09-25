import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RecommendationService } from '../../services/recommendation.service';
import { Recommendation } from '../../models/recommendation.model';

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

  constructor(private recommendationService: RecommendationService) {}

  ngOnInit(): void {
    this.loadStored();
  }

  setMode(mode: 'stored' | 'paste'): void {
    this.mode = mode;
    this.error = null;
    if (mode === 'stored') this.loadStored();
  }

  loadStored(): void {
    this.loading = true;
    this.recommendationService.getStoredRecommendations(10).subscribe({
      next: (res) => {
        this.recommendations = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the recommendation service. Is the backend running on :8080?';
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
    this.recommendationService.getRecommendationsFromList(this.rawText, 10).subscribe({
      next: (res) => {
        this.recommendations = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the recommendation service. Is the backend running on :8080?';
        this.loading = false;
      },
    });
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
