import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SlipService } from '../../services/slip.service';
import { SlipAnalysisResponse } from '../../models/slip.model';
import { SlipResultsComponent } from '../slip-results/slip-results.component';

// Every team here exists in the seeded strength database, so the sample
// demonstrates real modelled probabilities rather than league-average fallbacks.
const PLACEHOLDER = `Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10
Manchester City vs Arsenal - Over 2.5 @ 1.65
Real Madrid vs Barcelona - Draw @ 3.40`;

@Component({
  selector: 'app-slip-input',
  standalone: true,
  imports: [CommonModule, FormsModule, SlipResultsComponent],
  templateUrl: './slip-input.component.html',
  styleUrl: './slip-input.component.css',
})
export class SlipInputComponent {
  rawText = '';
  placeholder = PLACEHOLDER;
  loading = false;
  error: string | null = null;
  result: SlipAnalysisResponse | null = null;

  constructor(private slipService: SlipService) {}

  useSample(): void {
    this.rawText = this.placeholder;
  }

  analyze(): void {
    if (!this.rawText.trim()) {
      this.error = 'Paste at least one selection first.';
      return;
    }
    this.loading = true;
    this.error = null;
    this.result = null;

    this.slipService.analyze({ rawText: this.rawText }).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the analysis service. Is the backend running on :8080?';
        this.loading = false;
      },
    });
  }

  clear(): void {
    this.rawText = '';
    this.result = null;
    this.error = null;
  }
}
