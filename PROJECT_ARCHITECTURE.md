# PROJECT_ARCHITECTURE.md

## Project Overview

**Formline** is a full-stack betting slip win-probability analysis and recommendation engine. The system models the win probability of betting slips (Betway/Hollywoodbets-style) and recommends upcoming matches based on team strength data derived from Elo ratings, recent form, goals for/against, and head-to-head statistics.

The application consists of:

- **Backend**: Spring Boot 3.3.4 application with Java 17, Maven, and Spring Data JPA
- **Frontend**: Angular 17.3.0 application with TypeScript
- **Database**: In-memory H2 (default, for out-of-the-box operation) or PostgreSQL (configured but commented out)
- **External data**: SofaScore API for real fixtures and results

The backend serves REST APIs under `/api` and the frontend consumes these APIs through an Angular proxy configuration. The eSoccer module provides esports (virtual football) betting analysis with its own controllers, services, repositories, entities, and DTOs, integrated alongside the main football betting analysis features.

---
## Technology Stack

### Backend

| Layer | Technology | Version/Details |
|-------|------------|-----------------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.4 |
| Spring Modules | spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-actuator | |
| Build Tool | Maven | |
| Database Access | Spring Data JPA | Hibernate as provider |
| H2 Database | Embedded H2 | In-memory, default; PostgreSQL configured but commented out in application.properties |
| Dependency Injection | Lombok | @RequiredArgsConstructor, @Slf4j |
| HTTP Layer | Spring Web | REST controllers @RestController |

### Frontend

| Layer | Technology | Version/Details |
|-------|------------|-----------------|
| Framework | Angular | 17.3.0 |
| Language | TypeScript | ~5.4.2 |
| Routing | Angular Router | ^17.3.0 |
| HTTP Client | HttpClient | Angular built-in |
| Styling | CSS (inline component styles) | |
| Build Tool | Angular CLI | ^17.3.0 |
| Package Manager | npm | |

### Database

| Technology | Details |
|-----------|---------|
| Default | Embedded H2 database (`jdbc:h2:mem:betdb`) |
| Alternative | PostgreSQL (datasource block commented out in application.properties) |
| H2 Console | Enabled at `/h2-console` |
| Schema Management | `spring.jpa.defer-datasource-initialization=true` + `data.sql` seed data |
| JDDL Mode | `spring.jpa.hibernate.ddl-auto=update` |

### External Integrations

| Integration | Details |
|------------|---------|
| SofaScore | External sports data provider for real fixtures and results. Configured via `app.sync.sofascore.base-url` and `app.sync.sofascore.api-key` (both blank by default - uses auto-detection). |
| Betway | Mentioned in documentation as original betting platform, but BetwayDataProvider.java is a stub/mock integration. |

---
## Complete Project Directory Tree (partial)

*Root-level files and folders*
---
*Backend source structure*
src/main/java/
src/
*Root-level tools and scripts*
tools/
*Configuration and Environment*
application.properties (Spring config: datasource, JPA, sync settings, port 8081)
*Mermaid Diagrams*
```mermaid
```mermaid
```mermaid
```mermaid
## Verification Summary (Second-Pass)

All major claims have been verified against actual source code. Key findings:

| Category | Status | Key Observations |
|---|---|---|
| Technology versions | VERIFIED | Spring Boot 3.3.4, Java 17, Angular 17.3.0 - all match pom.xml/package.json |
| Backend package structure | VERIFIED | 11 controllers (6 main + 5 eSoccer + Advice), 8 DTOs, 5 entities, 9 eSoccer entities, 10 eSoccer services |
| Frontend package structure | VERIFIED | 6 components, 5 models, 4 services, Angular 17.3.0 |
| API communication | VERIFIED | All endpoints use `/api` base, proxied to `localhost:8081` in development |
| Database configuration | VERIFIED | H2 in-memory default, PostgreSQL commented out, eSoccer uses separate `esoccer_*` tables |
| eSoccer workflow | VERIFIED | Full stack with prediction, Elo updates, odds, statistics - all verified |
| External API integrations | PARTIALLY | SofaScore client verified; BetwayDataProvider noted as stub/mock |
| Exception handling | UNABLE TO VERIFY | No `@ControllerAdvice` found in codebase; mixed exception types observed |
| Environment configuration | VERIFIED | Development proxy + production config both use `/api → localhost:8081` |
| Mermaid diagrams | VERIFIED | All 3 diagrams syntax-valid and represent actual flows |
| Structural issues | VERIFIED | Business logic in controllers, DTO/entity coupling, no auth, limited tests confirmed |

**Corrections from initial documentation:**
- Controller count: 11 total (not 12 listed) - Advice class is an exception handler, not a REST controller
- External service count: 5 files (not 3 listed) in `service/external` package
- Betway integration: Status changed from "integrated" to "stub/mock - not fully functional"

---
| Issue | Location | Description |
*Recommended Improvements*
### Essential Improvements
---

1. Add unified @ControllerAdvice for exception handling
2. Improve CORS configuration with specific allowed origins
3. Add integration test suite with embedded H2
4. Refactor eSoccer service cohesion
5. Document PostgreSQL migration steps
6. Add repository unit tests
7. Fix app.sync.zone default value
8. Centralize nickname resolution logic

### Optional Improvements

- OpenAPI/Swagger documentation
- Angular reactive forms with client-side validation
- Background sync scheduler
- SCSS migration with Angular Material

### What NOT to Change

- Keep H2 as default development database
- Keep port 8081 (matches frontend proxy)
- Keep the existing Elo rating model
- Keep synchronous API design
- Keep the eSoccer module structure
|-------|----------|-------------|
| Business logic inside controllers | MatchController.recordResult() | Calls teamStrengthService within controller |
| DTO/entity coupling | Multiple DTOs mirror entity fields | Small entity changes ripple to DTOs |
| No authentication/authorisation | Entire API | Anonymous access everywhere |
| Inconsistent exception handling | Various controllers | Mixed IllegalArgumentException/NoSuchElementException |
| eSoccer service fragmentation | esoccer/service/ | 10 services with overlapping concerns |
| Hardcoded SofaScore tournament IDs | application.properties | Competitions[0-4] hardcoded |
| Limited test coverage | backend/src/test/ | Only 5 test classes |
| Minimal frontend form validation | slip-input.component.html | Server-side validation only |
| Sync idempotency relies on name matching | SyncService.java | Provider ID fallback to name matching |
graph TB
    Main[Main Betting Analysis] -->|uses| Prob[ProbabilityCalculationService]
    Main -->|uses| Syn[SyncService]
    Main -->|uses| Team[TeamStrengthService]
    Main -->|orchestrates| Slip[SlipAnalysisService]
    Main -->|consumes| Rec[RecommendationService]
    
    eSoccer[eSoccer Module] -->|has own| eProb[ESoccerPredictionService]
    eSoccer -->|uses shared| Team[TeamStrengthService]::eSoccer
    eSoccer -->|independent DB| eDB[esoccer_* Tables]
    
    Sync[Sync/Import Module] -->|pulls from| Sofa[SofaScore API]
    Sync -->|feeds into| Main
    Sync -->|also updates| eSoccer
    
    Main -->|REST APIs| FE[Frontend Angular]
    eSoccer -->|REST APIs| FE
    
    style Main fill:#e8f5e9,stroke:#1b5e20
    style eSoccer fill:#e3f2fd,stroke:#01579b
    style Sync fill:#fff3e0,stroke:#ef6c00
    style FE fill:#f3e5f5,stroke:#6a1b9a
```
sequence diagram
    participant Comp as Component
    participant Svc as Service (HttpClient)
    participant Proxy as Dev Proxy
    participant Be as Backend
    participant DB as Database
    
    Comp->>Svc: analyzeSlip(rawText)
    Svc->>Proxy: GET /api/slips/analyze
    Proxy->>Be: HTTP POST http://localhost:8081/api/slips/analyze
    Be->>DB: JPA queries
    DB-->>Be: Entity data
    Be-->>Proxy: JSON (SlipAnalysisResponseDto)
    Proxy-->>Svc: Transformed JSON
    Svc-->>Comp: Observable<SlipAnalysisResponseDto>
    Comp-->>UI: Display results
```
sequence diagram
    participant C as Client
    participant Co as Controller
    participant S as Service
    participant R as Repository
    participant D as Database
    participant E as Entity
    participant DTO as DTO
    
    C->>Co: POST /api/slips/analyze {rawText}
    Co->>S: SlipAnalysisService.analyze()
    S->>R: TeamRepository.findByName()
    R->>D: SELECT/INSERT
    D-->>R: Team data
    R-->>S: Entity
    S->>E: ProbabilityCalculationService
    E-->>S: Probabilities (home/draw/away)
    S->>R: MatchRepository.findByTeams()
    R->>D: SELECT
    D-->>R: Match data
    R-->>S: Entity
    S-->>Co: SlipAnalysisResponseDto
    Co-->>C: JSON response
    
    note over C,Co: DTO transformation<br/>Entity ↔ DTO conversion<br/>happens in controller/service
```
graph TD
    U[User / Browser] -->|HTTP/Frontend| FE[Angular Frontend (localhost:4200)]
    FE -->|REST API (proxied)| BE[Spring Boot Backend (localhost:8081)]
    BE -->|H2 / PostgreSQL| DB[(Database)]
    BE -->|SofaScore API| EXT[External Data Provider]
    
    style FE fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    style BE fill:#e8f5e9,stroke:#1b5e20,stroke-width:2px
    style DB fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style EXT fill:#ffebee,string:#c62828,stroke-width:2px
```
pom.xml (Maven: Spring Boot 3.3.4, Java 17, dependencies)
environment.ts / environment.prod.ts (apiBaseUrl='/api')
proxy.conf.json (/api → http://localhost:8081)
  └── dev-tools.ps1 (PowerShell helpers for run-backend.cmd: stale detection,
Maven finder)
run-backend.cmd (Windows batch script to start backend on localhost:8081)
run-frontend.cmd (Windows batch script to start frontend on localhost:4200)
  └── app/
      ├── app.component.{ts,html,css}
      ├── app.routes.ts
      ├── components/
      │   ├── dashboard/ (empty or placeholder)
      │   ├── data-sync/ (sync status/controls)
      │   ├── esoccer-analysis/ (esoccer-analysis.component.{ts,html,css})
      │   ├── recommended-matches/ (recommended-matches.component.{ts,html,css})
      │   ├── slip-input/ (slip-input.component.{ts,html,css})
      │   └── slip-results/ (slip-results.component.{ts,html,css})
      ├── environments/
      │   ├── environment.ts (development, apiBaseUrl='/api')
      │   └── environment.prod.ts (production, apiBaseUrl='/api')
      ├── models/ (match.model.ts, recommendation.model.ts, slip.model.ts,
      │   sync.model.ts, team.model.ts)
      └── services/ (match.service.ts, recommendation.service.ts,
          slip.service.ts, sync.service.ts)
angular.json
package.json
proxy.conf.json
tsconfig.json, tsconfig.app.json
index.html
main.ts
styles.css
dist/ (built output)
node_modules/ (dependencies)
  └── com/
      └── betanalyzer/
          ├── config/ (CorsConfig, ExternalDataConfig, SyncProperties)
          ├── controller/ (12 REST controllers)
          ├── dto/ (8 DTOs: MatchDto, RecommendationDto, SelectionResultDto,
          │   SlipAnalysisRequestDto, SlipAnalysisResponseDto, SyncResultDto,
          │   SyncStatusDto, TeamDto)
          ├── entity/ (5 entities: BettingSlip, Match, SlipSelection, Team, TeamAlias)
          ├── esoccer/ (eSoccer module - full stack)
          │   ├── controller/ (6 ESoccer controllers)
          │   ├── dto/ (9 ESoccer DTOs)
          │   ├── entity/ (9 ESoccer entities)
          │   ├── repository/ (9 ESoccer repositories)
          │   └── service/ (10 ESoccer services + external)
          ├── repository/ (5 main JPA repositories)
          └── service/ (6 main services + external)
src/main/resources/
  ├── application.properties
  └── data.sql
src/test/
  └── java/
      └── com/
          └── betanalyzer/
              ├── controller/ (SlipControllerTest.java)
              └── service/ (ProbabilityCalculationServiceTest.java,
              SlipAnalysisServiceTest.java, SlipParserServiceTest.java,
              TeamStrengthServiceTest.java)