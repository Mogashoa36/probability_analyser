import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { SyncService } from '../../services/sync.service';
import { SyncResult, SyncStatus } from '../../models/sync.model';

@Component({
  selector: 'app-data-sync',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './data-sync.component.html',
  styleUrl: './data-sync.component.css',
})
export class DataSyncComponent implements OnInit {
  status: SyncStatus | null = null;
  loading = false;
  syncing = false;
  error: string | null = null;
  results: SyncResult[] = [];

  /** Whether the "all leagues" sync should also import played matches. */
  includeResults = true;
  days = 7;

  constructor(private syncService: SyncService) {}

  ngOnInit(): void {
    this.loadStatus();
  }

  loadStatus(): void {
    this.loading = true;
    this.syncService.getStatus().subscribe({
      next: (res) => {
        this.status = res;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not reach the backend. Is it running on :8081?';
        this.loading = false;
      },
    });
  }

  syncToday(): void {
    this.run(() => this.syncService.syncToday());
  }

  syncNextDays(): void {
    this.run(() => this.syncService.syncNextDays(Number(this.days) || 7));
  }

  syncLeagues(): void {
    this.run(() => this.syncService.syncCompetitions(this.includeResults));
  }

  /** Runs a sync, keeps its report, and refreshes the status counters. */
  private run(request: () => Observable<SyncResult>): void {
    this.syncing = true;
    this.error = null;
    request().subscribe({
      next: (res) => {
        this.results.unshift(res);
        this.syncing = false;
        this.loadStatus();
      },
      error: (err) => {
        this.syncing = false;
        this.error =
          err?.error?.message ??
          'The sync request failed. The provider may be slow to respond - try again.';
      },
    });
  }

  /** Short label for the competition ids shown in the config list. */
  competitionLabel(entry: string): string {
    const idx = entry.indexOf(':');
    return idx > 0 ? entry.substring(idx + 1) : entry;
  }
}
