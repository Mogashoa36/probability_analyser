-- Sample teams with strength stats so the app has data to reason about immediately.
-- elo_rating: standard Elo baseline of 1500, adjust as real results come in.
-- form_points: points earned across last 5 games (max 15), used as a short-term momentum signal.
--
-- Rows are inserted WITHOUT literal ids on purpose. Seeding explicit ids leaves the
-- identity sequence at 1, so the very first POST /api/teams died with a primary-key
-- violation. Every statement is also guarded with NOT EXISTS so this file can safely
-- re-run on each boot (spring.sql.init.mode=always).

INSERT INTO team (name, league, elo_rating, form_points, goals_for_avg, goals_against_avg, home_advantage)
SELECT s.name, s.league, s.elo_rating, s.form_points, s.goals_for_avg, s.goals_against_avg, s.home_advantage
FROM (
         SELECT 'Kaizer Chiefs'     AS name, 'PSL'     AS league, 1512 AS elo_rating, 8 AS form_points, 1.20 AS goals_for_avg, 1.10 AS goals_against_avg, 55 AS home_advantage
         UNION ALL SELECT 'Orlando Pirates',   'PSL',     1560, 11, 1.60, 0.90, 55
         UNION ALL SELECT 'Mamelodi Sundowns', 'PSL',     1680, 13, 2.10, 0.70, 60
         UNION ALL SELECT 'Manchester City',   'EPL',     1820, 12, 2.40, 0.80, 65
         UNION ALL SELECT 'Arsenal',           'EPL',     1780, 13, 2.10, 0.75, 62
         UNION ALL SELECT 'Liverpool',         'EPL',     1795, 10, 2.20, 0.95, 63
         UNION ALL SELECT 'Chelsea',           'EPL',     1690,  9, 1.70, 1.10, 58
         UNION ALL SELECT 'Real Madrid',       'La Liga', 1830, 14, 2.30, 0.70, 64
         UNION ALL SELECT 'Barcelona',         'La Liga', 1800, 12, 2.35, 0.90, 62
     ) s
WHERE NOT EXISTS (SELECT 1 FROM team t WHERE t.name = s.name);

-- A handful of upcoming (SCHEDULED) fixtures so the Recommended Matches
-- page has something to rank straight away. Team ids are resolved by name so
-- the inserts stay correct whatever ids the identity sequence hands out.
INSERT INTO match_fixture (home_team_id, away_team_id, league, kick_off, status)
SELECT h.id, a.id, s.league, CAST(s.kick_off AS TIMESTAMP), 'SCHEDULED'
FROM (
         SELECT 'Mamelodi Sundowns' AS home_name, 'Kaizer Chiefs' AS away_name, 'PSL'     AS league, '2026-10-04 15:30:00' AS kick_off
         UNION ALL SELECT 'Orlando Pirates',   'Kaizer Chiefs',   'PSL',     '2026-10-05 15:00:00'
         UNION ALL SELECT 'Manchester City',   'Chelsea',         'EPL',     '2026-10-04 17:30:00'
         UNION ALL SELECT 'Liverpool',         'Arsenal',         'EPL',     '2026-10-05 16:00:00'
         UNION ALL SELECT 'Real Madrid',       'Barcelona',       'La Liga', '2026-10-04 20:00:00'
     ) s
         JOIN team h ON h.name = s.home_name
         JOIN team a ON a.name = s.away_name
WHERE NOT EXISTS (SELECT 1 FROM match_fixture m
                  WHERE m.home_team_id = h.id AND m.away_team_id = a.id);

-- Bookmakers and pasted bet slips use nicknames far more often than official club
-- names. Without these, "Man City" or "Barca" would fall back to league-average
-- figures and every affected leg would be flagged LOW confidence.
INSERT INTO team_alias (alias_name, team_id)
SELECT s.alias_name, t.id
FROM (
         SELECT 'Man City'     AS alias_name, 'Manchester City' AS team_name
         UNION ALL SELECT 'Man. City',    'Manchester City'
         UNION ALL SELECT 'Man Utd',      'Manchester United'
         UNION ALL SELECT 'Man United',   'Manchester United'
         UNION ALL SELECT 'Spurs',        'Tottenham Hotspur'
         UNION ALL SELECT 'Barca',        'Barcelona'
         UNION ALL SELECT 'FC Barcelona', 'Barcelona'
         UNION ALL SELECT 'Real',         'Real Madrid'
         UNION ALL SELECT 'Los Blancos',  'Real Madrid'
         UNION ALL SELECT 'The Citizens', 'Manchester City'
         UNION ALL SELECT 'The Gunners',  'Arsenal'
         UNION ALL SELECT 'The Reds',     'Liverpool'
         UNION ALL SELECT 'The Blues',    'Chelsea'
         UNION ALL SELECT 'Chiefs',       'Kaizer Chiefs'
         UNION ALL SELECT 'Pirates',      'Orlando Pirates'
         UNION ALL SELECT 'Sundowns',     'Mamelodi Sundowns'
     ) s
         JOIN team t ON t.name = s.team_name
WHERE NOT EXISTS (SELECT 1 FROM team_alias x WHERE x.alias_name = s.alias_name);