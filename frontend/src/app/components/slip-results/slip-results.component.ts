import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { SlipAnalysisResponse } from '../../models/slip.model';

@Component({
  selector: 'app-slip-results',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './slip-results.component.html',
  styleUrl: './slip-results.component.css',
})
export class SlipResultsComponent {
  @Input({ required: true }) result!: SlipAnalysisResponse;

  riskClass(rating: string): string {
    return 'risk-' + rating.toLowerCase().replace(' ', '-');
  }

  edgeClass(edge: number): string {
    if (edge > 5) return 'edge-positive';
    if (edge < -5) return 'edge-negative';
    return 'edge-neutral';
  }

  marketLabel(market: string): string {
    return market.replace(/_/g, ' ');
  }
}
