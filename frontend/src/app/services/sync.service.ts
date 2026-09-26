import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SyncResult, SyncStatus } from '../models/sync.model';
import { environment } from '../../environments/environment';

/**
 * Calls the backend's external-data endpoints. Every method is a POST except
 * the status read, because a sync writes to the database: it creates teams and
 * fixtures and moves Elo ratings when results come in.
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly baseUrl = `${environment.apiBaseUrl}/sync`;

  constructor(private http: HttpClient) {}

  /** Current provider configuration and what the last run did. */
  getStatus(): Observable<SyncStatus> {
    return this.http.get<SyncStatus>(`${this.baseUrl}/status`);
  }

  /** Today's fixtures across every competition the provider covers. */
  syncToday(): Observable<SyncResult> {
    return this.http.post<SyncResult>(`${this.baseUrl}/day`, null);
  }

  /** Upcoming fixtures for the next few days - the cheapest way to top up. */
  syncNextDays(days = 7): Observable<SyncResult> {
    return this.http.post<SyncResult>(`${this.baseUrl}/days`, null, { params: { days } });
  }

  /**
   * Every competition in app.sync.competitions. With results=true this also
   * imports played matches, which is what actually trains the strength model.
   */
  syncCompetitions(includeResults = true): Observable<SyncResult> {
    return this.http.post<SyncResult>(`${this.baseUrl}/competitions`, null, {
      params: { results: includeResults },
    });
  }
}
