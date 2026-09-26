# Formline — Betting Slip Win-Probability Analyser

A full-stack system for modelling the win probability of Betway/Hollywoodbets-style
betting slips and recommending upcoming matches, built from team strength data
(Elo rating, recent form, goals for/against).

## What it does

**Analyse Slip page**
- Paste a slip as free text (one selection per line) or structured JSON.
- Each leg is matched to known teams (or falls back to league-average
  stats for unknown teams) and scored with a modelled win probability.
- Shows the combined slip probability, total odds, implied probability,
  an overall risk rating, and refined suggestions (which legs to drop,
  which leg is weakest, whether the slip is over-extended, etc).

**Recommended Matches page**
- Ranks either your stored upcoming fixtures or a pasted list of
  fixtures ("Team A vs Team B" per line) by how confidently the model
  can call an outcome — a lopsided match scores higher than a close
  one, even at the same headline probability.
- Shows the kick-off **date and time** for every stored fixture, with a
  TODAY / TOMORROW / IN n DAYS badge and a crimson edge on anything
  kicking off within 48 hours.
- Filter the ranked list by team/league search, confidence band, market
  type (result vs goals), league, kick-off window (today / next 3 days /
  next 7 days) and a minimum model probability, with three sort orders.

## Pasting a slip

One selection per line. These forms are all understood:

```
Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10     # "@" price form
Man City v Arsenal, Over 2.5, 1.65                     # comma form
Real Madrid,Barcelona,DRAW,3.40                        # strict CSV
Tottenham vs Liverpool                                  # fixture only, no odds
```

Lines starting with `#` or `//` are ignored as comments.

**Nicknames resolve automatically.** Bookmakers never print official club
names, so a `team_alias` table maps the shorthand to the real club —
`Man City` → Manchester City, `Barca` → Barcelona, `The Gunners` → Arsenal.
Without a match the leg falls back to league-average figures and is
flagged `LOW` confidence, so responses always echo back the canonical
club name the probabilities were actually based on.

The price is read from **after an `@`**, or otherwise the **last** decimal
number on the line — market labels carry decimals of their own
(`Over 2.5`), so the goal line is never mistaken for the odds.

## The model

- **Match result probabilities** (home win / draw / away win): an
  Elo-style rating difference (including home advantage and a
  recent-form adjustment) run through a logistic curve, then split into
  home/draw/away using a draw-probability curve that narrows as the
  rating gap widens.
- **Goals markets** (Over/Under 2.5, BTTS): a Poisson goal-expectancy
  model built from each side's scoring/conceding averages — the
  standard approach in football analytics.
- **Elo ratings update automatically** whenever a match result is
  recorded (`POST /api/matches/{id}/result`), so the model keeps
  learning as results come in.

This is a genuine statistical model, not a black box — every
probability the API returns can be traced back to Elo rating, home
advantage, form, and expected goals.

## Project layout

```
run-backend.cmd    Start the backend (builds only if the jar is stale)
run-frontend.cmd   Start the Angular dev server
tools/
  dev-tools.ps1    Staleness / Maven lookup used by run-backend.cmd

backend/    Spring Boot (Java 17)
  entity/       Team, TeamAlias, Match, BettingSlip, SlipSelection
  repository/   Spring Data JPA repositories
  service/      ProbabilityCalculationService, SlipParserService,
                SlipAnalysisService, RecommendationService, TeamStrengthService
  controller/   SlipController, RecommendationController, TeamController, MatchController
  dto/          Request/response payloads
  config/       CORS

frontend/   Angular (standalone components, Angular 17)
  src/app/
    models/      team, match, slip, recommendation TypeScript interfaces
    services/    slip.service.ts, recommendation.service.ts, match.service.ts
    components/
      slip-input/            "Analyse Slip" page
      slip-results/          Leg-by-leg breakdown + suggestions (used by slip-input)
      recommended-matches/   "Recommended Matches" page (date/time + filters)
  src/styles.css   Theme tokens: black background, crimson/red/white palette
```

## Running it

The quickest path is the two scripts at the repo root — open two terminals
and run one in each:

```bash
run-backend.cmd     # http://localhost:8081
run-frontend.cmd    # http://localhost:4200
```

`run-backend.cmd` **does not need Maven installed**. It reuses the packaged
jar in `backend/target` and only rebuilds when that jar is missing or older
than the sources; if a build is needed it finds Maven on `PATH` or falls back
to any Maven already unpacked in `%USERPROFILE%\.m2\wrapper\dists`. Pass a
port to override, e.g. `run-backend.cmd 8082`.

> **Why port 8081 and not 8080?** A local Apache/`httpd` already holds 8080
> on this machine, so booting on 8080 dies with *"Port 8080 was already in
> use"* — the single most common reason the backend appears not to start.
> `backend/src/main/resources/application.properties` therefore defaults to
> **8081**, which is also what `frontend/proxy.conf.json` proxies `/api` to.
> If you change one, change the other, or pass `--server.port=NNNN`.

### Backend (manual)
```bash
cd backend
mvn spring-boot:run                                  # if Maven is installed
java -jar target/bet-analyzer-1.0.0.jar              # from the packaged jar
```
Starts on `http://localhost:8081`. Ships with an in-memory H2 database
seeded with sample teams and fixtures (`src/main/resources/data.sql`) so
it works immediately — no external DB needed. To use PostgreSQL instead,
swap the datasource block in `application.properties` (the Postgres
lines are already there, commented out) and add your own schema/seed data.

VS Code users can just press **F5** and pick *Backend: Spring Boot (:8081)*
(or the *Full stack* compound to launch both apps) — those use the Java
extension, so Maven is not required there either.

### Frontend
```bash
cd frontend
npm install
npm start
```
Starts on `http://localhost:4200` and proxies API calls to
`http://localhost:8081/api` (see `src/environments/environment.ts` and
`proxy.conf.json`).

## API quick reference

| Method | Endpoint                          | Purpose                                      |
|--------|------------------------------------|-----------------------------------------------|
| POST   | `/api/slips/analyze`               | Analyse a pasted or structured slip           |
| GET    | `/api/recommendations?limit=25`    | Ranked recommendations from stored fixtures   |
| POST   | `/api/recommendations/from-list`   | Ranked recommendations from a pasted list     |
| GET    | `/api/teams`                       | List team strength data                       |
| POST   | `/api/teams`                       | Add a team                                    |
| GET    | `/api/matches`                     | List fixtures                                 |
| POST   | `/api/matches/{id}/result`         | Record a result, updates Elo/form/goal stats  |

### Example: analyse a slip
```bash
curl -X POST http://localhost:8081/api/slips/analyze \
  -H "Content-Type: application/json" \
  -d '{"rawText": "Mamelodi Sundowns vs Kaizer Chiefs - Home Win @ 1.45\nManchester City vs Chelsea - Over 2.5 @ 1.70"}'
```

### Example: recommendations with kick-off times
```bash
curl "http://localhost:8081/api/recommendations?limit=2"
```
```json
[{
  "matchId": 1,
  "homeTeam": "Mamelodi Sundowns",
  "awayTeam": "Kaizer Chiefs",
  "league": "PSL",
  "kickOff": "2026-10-04T15:30:00",
  "recommendedMarket": "HOME_WIN",
  "modelProbabilityPct": 69.2,
  "confidenceScore": 64.4,
  "reasoning": "Mamelodi Sundowns at home rated at 69% ..."
}]
```
`kickOff` is a local ISO-8601 timestamp, and is `null` for fixtures that came
from the `from-list` endpoint (a pasted fixture carries no kick-off time).

## Notes & next steps
- Team/fixture data ships as a small sample set — add your real teams
  and results via `/api/teams` and `/api/matches` for sharper estimates
  (unknown teams still work, just fall back to league-average stats
  with a lower confidence rating).
- The combined slip probability assumes each leg is statistically
  independent, which is standard for an accumulator but won't hold
  perfectly for correlated markets (e.g. two markets on the same match).
- Model output is a statistical estimate based on historical data, not
  a guarantee of any outcome.
