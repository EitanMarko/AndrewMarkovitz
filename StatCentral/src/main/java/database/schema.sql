-- =============================================================================
-- schema.sql — PostgreSQL Database Schema for StatCentral (MLB configuration)
-- =============================================================================
--
-- This schema defines the full relational structure underpinning all five
-- microservices. The hierarchy is:
--
--   Conference → Division → Team → Player → PlayerStats (per year)
--
-- CASCADE rules are critical here: deleting a conference must automatically
-- delete all divisions within it, all teams within those divisions, all players
-- on those teams, and all stat rows for those players. This is enforced at the
-- database level so that no microservice needs to manually issue multiple
-- DELETE statements for a single logical "deleteConference" operation.
--
-- The schema is MLB-specific in the player_stats table (columns like home_runs,
-- hits, etc.), but the structural tables (conferences, divisions, teams, players)
-- are sport-agnostic and would work for any league.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Clean slate — drop tables in reverse dependency order if they exist.
-- Safe to run during development to reset the database.
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS mlb_player_stats CASCADE;
DROP TABLE IF EXISTS players      CASCADE;
DROP TABLE IF EXISTS teams        CASCADE;
DROP TABLE IF EXISTS divisions    CASCADE;
DROP TABLE IF EXISTS conferences  CASCADE;


-- -----------------------------------------------------------------------------
-- CONFERENCES
-- The top level of the league hierarchy.
-- Example: "AL", "NL" in MLB.
-- -----------------------------------------------------------------------------
CREATE TABLE conferences (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE   -- Conference names must be globally unique
);


-- -----------------------------------------------------------------------------
-- DIVISIONS
-- Each division belongs to exactly one conference.
-- The UNIQUE constraint is scoped to (name, conference_id): the same division
-- name can exist in different conferences (e.g. "East" in both AL and NL),
-- but not twice within the same conference.
-- ON DELETE CASCADE: deleting a conference deletes all its divisions.
-- -----------------------------------------------------------------------------
CREATE TABLE divisions (
    id            SERIAL       PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    conference_id INTEGER      NOT NULL REFERENCES conferences(id) ON DELETE CASCADE,
    UNIQUE (name, conference_id)
);


-- -----------------------------------------------------------------------------
-- TEAMS
-- Each team belongs to exactly one division.
-- Team names are globally unique (no two teams share a name across all leagues).
-- ON DELETE CASCADE: deleting a division deletes all its teams.
-- -----------------------------------------------------------------------------
CREATE TABLE teams (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    division_id INTEGER      NOT NULL REFERENCES divisions(id) ON DELETE CASCADE
);


-- -----------------------------------------------------------------------------
-- PLAYERS
-- Each player belongs to exactly one team.
-- Player names are treated as unique identifiers in this system (playerName == playerId).
-- ON DELETE CASCADE: deleting a team deletes all its players.
-- -----------------------------------------------------------------------------
CREATE TABLE players (
    id        SERIAL       PRIMARY KEY,
    name      VARCHAR(200) NOT NULL UNIQUE,   -- Used as the player's unique identifier
    position  VARCHAR(50),
    team_id   INTEGER      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    league_id VARCHAR(50)  NOT NULL DEFAULT 'MLB'  -- Which league this player belongs to
);


-- -----------------------------------------------------------------------------
-- MLB_PLAYER_STATS (MLB-specific)
-- Stores per-player, per-year statistics for MLB players.
-- Each (player_id, year) pair is unique — one stats row per player per season.
-- ON DELETE CASCADE: deleting a player deletes all their stat rows.
--
-- STAT COLUMNS:
--   home_runs       — total home runs hit that season
--   hits            — total hits (singles, doubles, triples, home runs)
--   at_bats         — total at-bat appearances (walks do NOT count as at-bats)
--   batting_average — computed as hits / at_bats; stored for query efficiency
--   strikeouts      — times the batter was struck out
--   walks           — times the batter received a base on balls (BB)
--   stolen_bases    — times the player successfully stole a base
--   runs_batted_in  — number of runners the batter drove home
--
-- NOTE ON BATTING AVERAGE:
--   batting_average is a derived stat (hits / at_bats). It is stored as a
--   column here for query performance — it would be expensive to compute
--   dynamically in every aggregate query. The MLBStatUpdater is responsible
--   for recomputing this value whenever hits or at_bats changes.
-- -----------------------------------------------------------------------------
CREATE TABLE mlb_player_stats (
    player_id       INTEGER        NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    year            INTEGER        NOT NULL,
    home_runs       INTEGER        NOT NULL DEFAULT 0,
    hits            INTEGER        NOT NULL DEFAULT 0,
    at_bats         INTEGER        NOT NULL DEFAULT 0,
    batting_average NUMERIC(5, 3)  NOT NULL DEFAULT 0.000,
    strikeouts      INTEGER        NOT NULL DEFAULT 0,
    walks           INTEGER        NOT NULL DEFAULT 0,
    stolen_bases    INTEGER        NOT NULL DEFAULT 0,
    runs_batted_in  INTEGER        NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, year)
);


-- -----------------------------------------------------------------------------
-- INDEXES
-- Speeds up common query patterns used by PlayerStatQuerier and
-- AggregateStatQuerier. Without indexes, every query would require a full
-- table scan.
-- -----------------------------------------------------------------------------

-- Fan queries frequently filter by year and sort by a stat column.
CREATE INDEX idx_mlb_player_stats_year ON mlb_player_stats(year);

-- AggregateStatQuerier JOINs teams → divisions → conferences frequently.
CREATE INDEX idx_teams_division    ON teams(division_id);
CREATE INDEX idx_divisions_conf    ON divisions(conference_id);
CREATE INDEX idx_players_team      ON players(team_id);

-- Lookups by name are the most common operation across all microservices.
-- The UNIQUE constraints above already create indexes on name columns,
-- but we add explicit ones here for documentation clarity.
-- (PostgreSQL automatically creates indexes for UNIQUE and PRIMARY KEY columns)

-- -----------------------------------------------------------------------------
-- NBA_PLAYER_STATS
-- Stores per-player, per-year statistics for NBA players.
-- The structure mirrors player_stats but with basketball-specific columns.
-- Each (player_id, year) pair is unique — one stats row per player per season.
-- ON DELETE CASCADE: deleting a player deletes all their NBA stat rows.
--
-- field_goal_percentage is a derived stat (field_goals_made / field_goals_attempted),
-- stored for query efficiency. NBAStatUpdater recomputes it whenever either
-- component changes.
-- -----------------------------------------------------------------------------
CREATE TABLE nba_player_stats (
    player_id             INTEGER       NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    year                  INTEGER       NOT NULL,
    points                INTEGER       NOT NULL DEFAULT 0,
    rebounds              INTEGER       NOT NULL DEFAULT 0,
    assists               INTEGER       NOT NULL DEFAULT 0,
    steals                INTEGER       NOT NULL DEFAULT 0,
    blocks                INTEGER       NOT NULL DEFAULT 0,
    turnovers             INTEGER       NOT NULL DEFAULT 0,
    field_goals_made      INTEGER       NOT NULL DEFAULT 0,
    field_goals_attempted INTEGER       NOT NULL DEFAULT 0,
    field_goal_percentage NUMERIC(5, 3) NOT NULL DEFAULT 0.000,
    PRIMARY KEY (player_id, year)
);


-- -----------------------------------------------------------------------------
-- SAMPLE DATA — useful for local development and testing.
-- Creates a minimal AL East structure with one player.
-- Comment this section out in production.
-- -----------------------------------------------------------------------------

INSERT INTO conferences (name) VALUES ('AL'), ('NL');

INSERT INTO divisions (name, conference_id)
VALUES ('East', (SELECT id FROM conferences WHERE name = 'AL')),
       ('West', (SELECT id FROM conferences WHERE name = 'AL')),
       ('East', (SELECT id FROM conferences WHERE name = 'NL'));

INSERT INTO teams (name, division_id)
VALUES ('New York Yankees', (SELECT d.id FROM divisions d
                              JOIN conferences c ON d.conference_id = c.id
                              WHERE d.name = 'East' AND c.name = 'AL')),
       ('Boston Red Sox',   (SELECT d.id FROM divisions d
                              JOIN conferences c ON d.conference_id = c.id
                              WHERE d.name = 'East' AND c.name = 'AL'));

INSERT INTO players (name, position, team_id)
VALUES ('Aaron Judge', 'RF', (SELECT id FROM teams WHERE name = 'New York Yankees'));

INSERT INTO mlb_player_stats (player_id, year, home_runs, hits, at_bats, batting_average,
                               strikeouts, walks, stolen_bases, runs_batted_in)
VALUES ((SELECT id FROM players WHERE name = 'Aaron Judge'),
        2024, 58, 158, 531, 0.322, 130, 133, 10, 144);
