import { Routes } from '@angular/router';
import { SlipInputComponent } from './components/slip-input/slip-input.component';
import { RecommendedMatchesComponent } from './components/recommended-matches/recommended-matches.component';

export const routes: Routes = [
  { path: '', redirectTo: 'analyze', pathMatch: 'full' },
  { path: 'analyze', component: SlipInputComponent },
  { path: 'recommendations', component: RecommendedMatchesComponent },
  { path: '**', redirectTo: 'analyze' },
];
