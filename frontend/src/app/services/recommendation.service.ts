import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Recommendation } from '../models/recommendation.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  private readonly baseUrl = `${environment.apiBaseUrl}/recommendations`;

  constructor(private http: HttpClient) {}

  /** Recommendations from fixtures already stored on the backend. */
  getStoredRecommendations(limit = 10): Observable<Recommendation[]> {
    return this.http.get<Recommendation[]>(this.baseUrl, { params: { limit } });
  }

  /** Recommendations from a pasted list of upcoming fixtures. */
  getRecommendationsFromList(rawText: string, limit = 10): Observable<Recommendation[]> {
    return this.http.post<Recommendation[]>(`${this.baseUrl}/from-list`, { rawText, limit });
  }
}
