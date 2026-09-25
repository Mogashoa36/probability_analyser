import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SlipAnalysisRequest, SlipAnalysisResponse } from '../models/slip.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SlipService {
  private readonly baseUrl = `${environment.apiBaseUrl}/slips`;

  constructor(private http: HttpClient) {}

  analyze(request: SlipAnalysisRequest): Observable<SlipAnalysisResponse> {
    return this.http.post<SlipAnalysisResponse>(`${this.baseUrl}/analyze`, request);
  }
}
