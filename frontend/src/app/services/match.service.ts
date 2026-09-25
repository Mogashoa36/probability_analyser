import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Match } from '../models/match.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MatchService {
  private readonly baseUrl = `${environment.apiBaseUrl}/matches`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Match[]> {
    return this.http.get<Match[]>(this.baseUrl);
  }

  recordResult(matchId: number, homeGoals: number, awayGoals: number): Observable<Match> {
    return this.http.post<Match>(`${this.baseUrl}/${matchId}/result`, null, {
      params: { homeGoals, awayGoals },
    });
  }
}
