import { Component } from "@angular/core";
import { CommonModule } from "@angular/common";

@Component({
  selector: "app-esoccer-analysis",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./esoccer-analysis.component.html",
  styleUrl: "./esoccer-analysis.component.css"
})
export class EsoccerAnalysisComponent {
  title = "eSoccer Analysis";
  prediction: any = null;
}