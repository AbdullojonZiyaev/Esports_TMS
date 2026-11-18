-- =====================================================================
--  ISTE 330 Group 1 - Esports Tournament Management System
--  ONE script: creates the database, tables, keys, constraints,
--  stored procedures, views (reports) and loads all sample data.
--
--  HOW TO RUN (pick one):
--    MySQL Workbench : File > Open SQL Script > esports_tms.sql > Execute (lightning bolt)
--    Command line    : mysql -u root -p < esports_tms.sql
--
--  Safe to run again at any time: it drops and rebuilds esports_tms.
--  Tested on MySQL 8.0+/9.x and MariaDB 11+.
-- =====================================================================

DROP DATABASE IF EXISTS esports_tms;
CREATE DATABASE esports_tms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE esports_tms;

-- ---------------------------------------------------------------------
-- 1. TABLES (12 tables, all in 3NF)
-- ---------------------------------------------------------------------

-- One login per person. Passwords are stored ONLY as SHA-256(salt + password).
CREATE TABLE UserAccount (
    user_id        INT          NOT NULL AUTO_INCREMENT,
    username       VARCHAR(30)  NOT NULL,
    password_hash  CHAR(64)     NOT NULL,
    password_salt  CHAR(32)     NOT NULL,
    role           ENUM('DIRECTOR','COACH','PLAYER') NOT NULL,
    email          VARCHAR(100) NOT NULL,
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_useraccount PRIMARY KEY (user_id),
    CONSTRAINT uq_useraccount_username UNIQUE (username),
    CONSTRAINT uq_useraccount_email UNIQUE (email)
);

CREATE TABLE Director (
    director_id  INT         NOT NULL AUTO_INCREMENT,
    user_id      INT         NOT NULL,
    first_name   VARCHAR(50) NOT NULL,
    last_name    VARCHAR(50) NOT NULL,
    phone        VARCHAR(20) NULL,
    CONSTRAINT pk_director PRIMARY KEY (director_id),
    CONSTRAINT uq_director_user UNIQUE (user_id),
    CONSTRAINT fk_director_user FOREIGN KEY (user_id) REFERENCES UserAccount(user_id)
);

CREATE TABLE Coach (
    coach_id          INT         NOT NULL AUTO_INCREMENT,
    user_id           INT         NOT NULL,
    first_name        VARCHAR(50) NOT NULL,
    last_name         VARCHAR(50) NOT NULL,
    phone             VARCHAR(20) NULL,
    years_experience  TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT pk_coach PRIMARY KEY (coach_id),
    CONSTRAINT uq_coach_user UNIQUE (user_id),
    CONSTRAINT fk_coach_user FOREIGN KEY (user_id) REFERENCES UserAccount(user_id),
    CONSTRAINT ck_coach_experience CHECK (years_experience BETWEEN 0 AND 60)
);

CREATE TABLE Player (
    player_id      INT         NOT NULL AUTO_INCREMENT,
    user_id        INT         NOT NULL,
    first_name     VARCHAR(50) NOT NULL,
    last_name      VARCHAR(50) NOT NULL,
    gamer_tag      VARCHAR(30) NOT NULL,
    date_of_birth  DATE        NOT NULL,
    country        VARCHAR(50) NOT NULL,
    CONSTRAINT pk_player PRIMARY KEY (player_id),
    CONSTRAINT uq_player_user UNIQUE (user_id),
    CONSTRAINT uq_player_gamertag UNIQUE (gamer_tag),
    CONSTRAINT fk_player_user FOREIGN KEY (user_id) REFERENCES UserAccount(user_id)
);

CREATE TABLE Game (
    game_id           INT         NOT NULL AUTO_INCREMENT,
    game_name         VARCHAR(50) NOT NULL,
    genre             VARCHAR(30) NOT NULL,
    publisher         VARCHAR(50) NOT NULL,
    players_per_team  TINYINT     NOT NULL,
    CONSTRAINT pk_game PRIMARY KEY (game_id),
    CONSTRAINT uq_game_name UNIQUE (game_name),
    CONSTRAINT ck_game_players CHECK (players_per_team BETWEEN 1 AND 10)
);

CREATE TABLE Team (
    team_id       INT         NOT NULL AUTO_INCREMENT,
    team_name     VARCHAR(50) NOT NULL,
    game_id       INT         NOT NULL,
    coach_id      INT         NOT NULL,
    region        VARCHAR(30) NOT NULL,
    founded_date  DATE        NOT NULL,
    CONSTRAINT pk_team PRIMARY KEY (team_id),
    CONSTRAINT uq_team_name UNIQUE (team_name),
    CONSTRAINT fk_team_game  FOREIGN KEY (game_id)  REFERENCES Game(game_id),
    CONSTRAINT fk_team_coach FOREIGN KEY (coach_id) REFERENCES Coach(coach_id)
);

-- M:N #1  Player <-> Team.  leave_date NULL = current member.
CREATE TABLE TeamMembership (
    team_id     INT         NOT NULL,
    player_id   INT         NOT NULL,
    join_date   DATE        NOT NULL,
    leave_date  DATE        NULL,
    team_role   ENUM('CAPTAIN','STARTER','SUBSTITUTE') NOT NULL DEFAULT 'STARTER',
    CONSTRAINT pk_teammembership PRIMARY KEY (team_id, player_id),
    CONSTRAINT fk_tm_team   FOREIGN KEY (team_id)   REFERENCES Team(team_id),
    CONSTRAINT fk_tm_player FOREIGN KEY (player_id) REFERENCES Player(player_id),
    CONSTRAINT ck_tm_dates CHECK (leave_date IS NULL OR leave_date >= join_date)
);

CREATE TABLE Tournament (
    tournament_id    INT           NOT NULL AUTO_INCREMENT,
    tournament_name  VARCHAR(80)   NOT NULL,
    game_id          INT           NOT NULL,
    director_id      INT           NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    location         VARCHAR(80)   NOT NULL,
    prize_pool       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    max_teams        TINYINT       NOT NULL,
    status           ENUM('UPCOMING','ONGOING','COMPLETED') NOT NULL DEFAULT 'UPCOMING',
    CONSTRAINT pk_tournament PRIMARY KEY (tournament_id),
    CONSTRAINT uq_tournament_name UNIQUE (tournament_name),
    CONSTRAINT fk_tournament_game     FOREIGN KEY (game_id)     REFERENCES Game(game_id),
    CONSTRAINT fk_tournament_director FOREIGN KEY (director_id) REFERENCES Director(director_id),
    CONSTRAINT ck_tournament_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_tournament_prize CHECK (prize_pool >= 0),
    CONSTRAINT ck_tournament_maxteams CHECK (max_teams BETWEEN 2 AND 64)
);

-- M:N #2  Team <-> Tournament.  Deleting a tournament removes its registrations.
CREATE TABLE TournamentRegistration (
    tournament_id      INT  NOT NULL,
    team_id            INT  NOT NULL,
    registration_date  DATE NOT NULL,
    seed_number        TINYINT NULL,
    CONSTRAINT pk_tournamentregistration PRIMARY KEY (tournament_id, team_id),
    CONSTRAINT fk_tr_tournament FOREIGN KEY (tournament_id) REFERENCES Tournament(tournament_id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_team       FOREIGN KEY (team_id)       REFERENCES Team(team_id)
);

-- MATCH is a reserved word in MySQL, so the table is called TournamentMatch.
-- The winner is NOT stored; it is calculated from the scores in the views.
CREATE TABLE TournamentMatch (
    match_id       INT         NOT NULL AUTO_INCREMENT,
    tournament_id  INT         NOT NULL,
    home_team_id   INT         NOT NULL,
    away_team_id   INT         NOT NULL,
    round_name     VARCHAR(30) NOT NULL,
    scheduled_at   DATETIME    NOT NULL,
    venue          VARCHAR(80) NOT NULL,
    home_score     TINYINT     NULL,
    away_score     TINYINT     NULL,
    status         ENUM('SCHEDULED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT pk_tournamentmatch PRIMARY KEY (match_id),
    CONSTRAINT fk_match_tournament FOREIGN KEY (tournament_id) REFERENCES Tournament(tournament_id) ON DELETE CASCADE,
    CONSTRAINT fk_match_home FOREIGN KEY (home_team_id) REFERENCES Team(team_id),
    CONSTRAINT fk_match_away FOREIGN KEY (away_team_id) REFERENCES Team(team_id),
    CONSTRAINT ck_match_teams CHECK (home_team_id <> away_team_id)
);

-- M:N #3  Player <-> TournamentMatch (per-player statistics for a match).
CREATE TABLE PlayerMatchStat (
    match_id   INT      NOT NULL,
    player_id  INT      NOT NULL,
    kills      SMALLINT NOT NULL DEFAULT 0,
    deaths     SMALLINT NOT NULL DEFAULT 0,
    assists    SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_playermatchstat PRIMARY KEY (match_id, player_id),
    CONSTRAINT fk_pms_match  FOREIGN KEY (match_id)  REFERENCES TournamentMatch(match_id) ON DELETE CASCADE,
    CONSTRAINT fk_pms_player FOREIGN KEY (player_id) REFERENCES Player(player_id),
    CONSTRAINT ck_pms_values CHECK (kills >= 0 AND deaths >= 0 AND assists >= 0)
);

-- A player asks to join a team; the team's coach approves or declines.
-- (Player <-> Team requests; the membership itself is still TeamMembership.)
CREATE TABLE JoinRequest (
    request_id      INT          NOT NULL AUTO_INCREMENT,
    team_id         INT          NOT NULL,
    player_id       INT          NOT NULL,
    requested_role  ENUM('CAPTAIN','STARTER','SUBSTITUTE') NOT NULL DEFAULT 'STARTER',
    message         VARCHAR(200) NULL,
    status          ENUM('PENDING','APPROVED','DECLINED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    requested_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at     DATETIME     NULL,
    CONSTRAINT pk_joinrequest PRIMARY KEY (request_id),
    CONSTRAINT fk_jr_team   FOREIGN KEY (team_id)   REFERENCES Team(team_id),
    CONSTRAINT fk_jr_player FOREIGN KEY (player_id) REFERENCES Player(player_id),
    CONSTRAINT ck_jr_answered CHECK (answered_at IS NULL OR answered_at >= requested_at)
);

-- ---------------------------------------------------------------------
-- 2. STORED PROCEDURES (called from Java with CallableStatement)
--    Each one checks the business rules first and returns
--    p_success (1/0) and a plain-English p_message.
-- ---------------------------------------------------------------------
DELIMITER $$

CREATE PROCEDURE sp_RegisterTeamForTournament(
    IN  p_tournament_id INT,
    IN  p_team_id       INT,
    OUT p_success       TINYINT,
    OUT p_message       VARCHAR(200))
proc: BEGIN
    DECLARE v_status     VARCHAR(20);
    DECLARE v_t_game     INT;
    DECLARE v_max        INT;
    DECLARE v_team_game  INT;
    DECLARE v_count      INT;

    DECLARE CONTINUE HANDLER FOR NOT FOUND BEGIN END;

    SET p_success = 0;

    SELECT status, game_id, max_teams INTO v_status, v_t_game, v_max
      FROM Tournament WHERE tournament_id = p_tournament_id;
    IF v_status IS NULL THEN
        SET p_message = 'Tournament not found.';
        LEAVE proc;
    END IF;
    IF v_status <> 'UPCOMING' THEN
        SET p_message = CONCAT('Registration is closed (tournament is ', v_status, ').');
        LEAVE proc;
    END IF;

    SELECT game_id INTO v_team_game FROM Team WHERE team_id = p_team_id;
    IF v_team_game IS NULL THEN
        SET p_message = 'Team not found.';
        LEAVE proc;
    END IF;
    IF v_team_game <> v_t_game THEN
        SET p_message = 'This team does not play the game of this tournament.';
        LEAVE proc;
    END IF;

    SELECT COUNT(*) INTO v_count FROM TournamentRegistration
     WHERE tournament_id = p_tournament_id AND team_id = p_team_id;
    IF v_count > 0 THEN
        SET p_message = 'This team is already registered for this tournament.';
        LEAVE proc;
    END IF;

    SELECT COUNT(*) INTO v_count FROM TournamentRegistration
     WHERE tournament_id = p_tournament_id;
    IF v_count >= v_max THEN
        SET p_message = 'Tournament is full.';
        LEAVE proc;
    END IF;

    INSERT INTO TournamentRegistration (tournament_id, team_id, registration_date, seed_number)
    VALUES (p_tournament_id, p_team_id, CURRENT_DATE, v_count + 1);

    SET p_success = 1;
    SET p_message = CONCAT('Team registered. Seed number ', v_count + 1, '.');
END$$

CREATE PROCEDURE sp_AddPlayerToTeam(
    IN  p_team_id    INT,
    IN  p_player_id  INT,
    IN  p_team_role  VARCHAR(20),
    OUT p_success    TINYINT,
    OUT p_message    VARCHAR(200))
proc: BEGIN
    DECLARE v_max      INT;
    DECLARE v_count    INT;
    DECLARE v_player   INT;
    DECLARE v_leave    DATE;
    DECLARE v_member   INT DEFAULT 0;

    DECLARE CONTINUE HANDLER FOR NOT FOUND BEGIN END;

    SET p_success = 0;

    IF p_team_role NOT IN ('CAPTAIN','STARTER','SUBSTITUTE') THEN
        SET p_message = 'Role must be CAPTAIN, STARTER or SUBSTITUTE.';
        LEAVE proc;
    END IF;

    SELECT g.players_per_team INTO v_max
      FROM Team t JOIN Game g ON g.game_id = t.game_id
     WHERE t.team_id = p_team_id;
    IF v_max IS NULL THEN
        SET p_message = 'Team not found.';
        LEAVE proc;
    END IF;

    SELECT player_id INTO v_player FROM Player WHERE player_id = p_player_id;
    IF v_player IS NULL THEN
        SET p_message = 'Player not found.';
        LEAVE proc;
    END IF;

    SELECT COUNT(*), MAX(leave_date) INTO v_member, v_leave
      FROM TeamMembership WHERE team_id = p_team_id AND player_id = p_player_id;
    IF v_member > 0 AND v_leave IS NULL THEN
        SET p_message = 'Player is already on this team.';
        LEAVE proc;
    END IF;

    -- roster limit = players per team + 2 substitutes
    SELECT COUNT(*) INTO v_count FROM TeamMembership
     WHERE team_id = p_team_id AND leave_date IS NULL;
    IF v_count >= v_max + 2 THEN
        SET p_message = 'Roster is full.';
        LEAVE proc;
    END IF;

    IF v_member > 0 THEN
        -- former member re-joins: reopen the membership
        UPDATE TeamMembership
           SET join_date = CURRENT_DATE, leave_date = NULL, team_role = p_team_role
         WHERE team_id = p_team_id AND player_id = p_player_id;
    ELSE
        INSERT INTO TeamMembership (team_id, player_id, join_date, team_role)
        VALUES (p_team_id, p_player_id, CURRENT_DATE, p_team_role);
    END IF;

    -- a request from this player to this team is answered now
    UPDATE JoinRequest SET status = 'APPROVED', answered_at = NOW()
     WHERE team_id = p_team_id AND player_id = p_player_id AND status = 'PENDING';

    SET p_success = 1;
    SET p_message = 'Player added to team.';
END$$

CREATE PROCEDURE sp_RecordMatchResult(
    IN  p_match_id    INT,
    IN  p_home_score  INT,
    IN  p_away_score  INT,
    OUT p_success     TINYINT,
    OUT p_message     VARCHAR(200))
proc: BEGIN
    DECLARE v_status VARCHAR(20);

    DECLARE CONTINUE HANDLER FOR NOT FOUND BEGIN END;

    SET p_success = 0;

    SELECT status INTO v_status FROM TournamentMatch WHERE match_id = p_match_id;
    IF v_status IS NULL THEN
        SET p_message = 'Match not found.';
        LEAVE proc;
    END IF;
    IF v_status <> 'SCHEDULED' THEN
        SET p_message = CONCAT('Only SCHEDULED matches can get a result (this one is ', v_status, ').');
        LEAVE proc;
    END IF;
    IF p_home_score < 0 OR p_away_score < 0 OR p_home_score > 99 OR p_away_score > 99 THEN
        SET p_message = 'Scores must be between 0 and 99.';
        LEAVE proc;
    END IF;
    IF p_home_score = p_away_score THEN
        SET p_message = 'Draws are not allowed. One team must win.';
        LEAVE proc;
    END IF;

    UPDATE TournamentMatch
       SET home_score = p_home_score, away_score = p_away_score, status = 'COMPLETED'
     WHERE match_id = p_match_id;

    SET p_success = 1;
    SET p_message = 'Result recorded. Match is now COMPLETED.';
END$$

-- A coach approves a join request. Uses sp_AddPlayerToTeam, so the same
-- roster rules apply (roster not full, player not already on the team).
CREATE PROCEDURE sp_ApproveJoinRequest(
    IN  p_request_id  INT,
    IN  p_coach_id    INT,
    IN  p_team_role   VARCHAR(20),
    OUT p_success     TINYINT,
    OUT p_message     VARCHAR(200))
proc: BEGIN
    DECLARE v_team    INT;
    DECLARE v_player  INT;
    DECLARE v_status  VARCHAR(20);
    DECLARE v_coach   INT;
    DECLARE v_ok      TINYINT;
    DECLARE v_msg     VARCHAR(200);

    DECLARE CONTINUE HANDLER FOR NOT FOUND BEGIN END;

    SET p_success = 0;

    SELECT r.team_id, r.player_id, r.status, t.coach_id INTO v_team, v_player, v_status, v_coach
      FROM JoinRequest r JOIN Team t ON t.team_id = r.team_id
     WHERE r.request_id = p_request_id;
    IF v_team IS NULL THEN
        SET p_message = 'Request not found.';
        LEAVE proc;
    END IF;
    IF v_coach <> p_coach_id THEN
        SET p_message = 'Only the coach of this team can answer this request.';
        LEAVE proc;
    END IF;
    IF v_status <> 'PENDING' THEN
        SET p_message = CONCAT('This request was already answered (', v_status, ').');
        LEAVE proc;
    END IF;

    CALL sp_AddPlayerToTeam(v_team, v_player, p_team_role, v_ok, v_msg);
    IF v_ok = 0 THEN
        SET p_message = v_msg;
        LEAVE proc;
    END IF;

    SET p_success = 1;
    SET p_message = 'Request approved. The player is now on the team.';
END$$

-- A team drops out of a tournament (coach), or the director removes it.
-- Not allowed once the team has played a match in it, or when the tournament is over.
-- The team's matches that were not played yet are CANCELLED.
CREATE PROCEDURE sp_WithdrawTeamFromTournament(
    IN  p_tournament_id INT,
    IN  p_team_id       INT,
    OUT p_success       TINYINT,
    OUT p_message       VARCHAR(200))
proc: BEGIN
    DECLARE v_status   VARCHAR(20);
    DECLARE v_count    INT;
    DECLARE v_played   INT;

    DECLARE CONTINUE HANDLER FOR NOT FOUND BEGIN END;

    SET p_success = 0;

    SELECT status INTO v_status FROM Tournament WHERE tournament_id = p_tournament_id;
    IF v_status IS NULL THEN
        SET p_message = 'Tournament not found.';
        LEAVE proc;
    END IF;
    IF v_status = 'COMPLETED' THEN
        SET p_message = 'This tournament is over. Teams can no longer leave it.';
        LEAVE proc;
    END IF;

    SELECT COUNT(*) INTO v_count FROM TournamentRegistration
     WHERE tournament_id = p_tournament_id AND team_id = p_team_id;
    IF v_count = 0 THEN
        SET p_message = 'This team is not in this tournament.';
        LEAVE proc;
    END IF;

    SELECT COUNT(*) INTO v_played FROM TournamentMatch
     WHERE tournament_id = p_tournament_id AND status = 'COMPLETED'
       AND (home_team_id = p_team_id OR away_team_id = p_team_id);
    IF v_played > 0 THEN
        SET p_message = CONCAT('This team already played ', v_played,
                               ' match(es) in this tournament, so it cannot leave it now.');
        LEAVE proc;
    END IF;

    UPDATE TournamentMatch SET status = 'CANCELLED'
     WHERE tournament_id = p_tournament_id AND status = 'SCHEDULED'
       AND (home_team_id = p_team_id OR away_team_id = p_team_id);
    SET v_count = ROW_COUNT();

    DELETE FROM TournamentRegistration
     WHERE tournament_id = p_tournament_id AND team_id = p_team_id;

    SET p_success = 1;
    SET p_message = CONCAT('Team removed from the tournament. ', v_count,
                           ' planned match(es) cancelled.');
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 3. VIEWS (one per report)
-- ---------------------------------------------------------------------

-- Report 1: match schedule and results
CREATE VIEW vw_MatchSchedule AS
SELECT m.match_id,
       t.tournament_id,
       t.tournament_name,
       m.round_name,
       m.scheduled_at,
       m.venue,
       m.home_team_id,
       ht.team_name AS home_team,
       m.away_team_id,
       aw.team_name AS away_team,
       m.home_score,
       m.away_score,
       CASE WHEN m.status <> 'COMPLETED' THEN NULL
            WHEN m.home_score > m.away_score THEN ht.team_name
            ELSE aw.team_name END AS winner,
       m.status
  FROM TournamentMatch m
  JOIN Tournament t ON t.tournament_id = m.tournament_id
  JOIN Team ht ON ht.team_id = m.home_team_id
  JOIN Team aw ON aw.team_id = m.away_team_id;

-- Report 2: standings (3 points per win). Every registered team appears, even with 0 games.
CREATE VIEW vw_TournamentStandings AS
SELECT r.tournament_id,
       t.tournament_name,
       tm.team_id,
       tm.team_name,
       COUNT(x.match_id)                                   AS played,
       COALESCE(SUM(x.scored > x.conceded), 0)             AS wins,
       COALESCE(SUM(x.scored < x.conceded), 0)             AS losses,
       COALESCE(SUM(x.scored), 0)                          AS score_for,
       COALESCE(SUM(x.conceded), 0)                        AS score_against,
       COALESCE(SUM(x.scored) - SUM(x.conceded), 0)        AS score_diff,
       COALESCE(SUM(x.scored > x.conceded), 0) * 3         AS points
  FROM TournamentRegistration r
  JOIN Tournament t ON t.tournament_id = r.tournament_id
  JOIN Team tm      ON tm.team_id = r.team_id
  LEFT JOIN (
        SELECT match_id, tournament_id, home_team_id AS team_id,
               home_score AS scored, away_score AS conceded
          FROM TournamentMatch WHERE status = 'COMPLETED'
        UNION ALL
        SELECT match_id, tournament_id, away_team_id,
               away_score, home_score
          FROM TournamentMatch WHERE status = 'COMPLETED'
       ) x ON x.tournament_id = r.tournament_id AND x.team_id = r.team_id
 GROUP BY r.tournament_id, t.tournament_name, tm.team_id, tm.team_name;

-- Report 3: registrations per tournament
CREATE VIEW vw_TournamentRegistrations AS
SELECT t.tournament_id,
       t.tournament_name,
       g.game_name,
       t.status AS tournament_status,
       tm.team_id,
       tm.team_name,
       CONCAT(c.first_name, ' ', c.last_name) AS coach_name,
       r.registration_date,
       r.seed_number
  FROM TournamentRegistration r
  JOIN Tournament t ON t.tournament_id = r.tournament_id
  JOIN Game g       ON g.game_id = t.game_id
  JOIN Team tm      ON tm.team_id = r.team_id
  JOIN Coach c      ON c.coach_id = tm.coach_id;

-- Report 4: current team rosters
CREATE VIEW vw_TeamRoster AS
SELECT tm.team_id,
       tm.team_name,
       g.game_name,
       CONCAT(c.first_name, ' ', c.last_name) AS coach_name,
       p.player_id,
       p.gamer_tag,
       CONCAT(p.first_name, ' ', p.last_name) AS player_name,
       p.country,
       m.team_role,
       m.join_date
  FROM TeamMembership m
  JOIN Team tm  ON tm.team_id = m.team_id
  JOIN Game g   ON g.game_id = tm.game_id
  JOIN Coach c  ON c.coach_id = tm.coach_id
  JOIN Player p ON p.player_id = m.player_id
 WHERE m.leave_date IS NULL;

-- Report 5: player statistics across all completed matches
CREATE VIEW vw_PlayerStatistics AS
SELECT p.player_id,
       p.gamer_tag,
       CONCAT(p.first_name, ' ', p.last_name) AS player_name,
       COUNT(s.match_id)              AS matches_played,
       COALESCE(SUM(s.kills), 0)      AS total_kills,
       COALESCE(SUM(s.deaths), 0)     AS total_deaths,
       COALESCE(SUM(s.assists), 0)    AS total_assists,
       ROUND(COALESCE(SUM(s.kills), 0) / GREATEST(COALESCE(SUM(s.deaths), 0), 1), 2) AS kd_ratio
  FROM Player p
  LEFT JOIN PlayerMatchStat s ON s.player_id = p.player_id
 GROUP BY p.player_id, p.gamer_tag, p.first_name, p.last_name;

-- ---------------------------------------------------------------------
-- 4. SAMPLE DATA
--    Test logins (every account of a role has the same password):
--      Tournament Director : director1 / Director123
--      Coach               : coach1    / Coach123
--      Player              : player1   / Player123
--    Passwords are typed here ONLY inside a temporary table; the script
--    stores SHA-256(salt + password) and then drops the temporary table.
-- ---------------------------------------------------------------------

CREATE TEMPORARY TABLE seed_login (
    username VARCHAR(30), plain_password VARCHAR(50), role VARCHAR(10), email VARCHAR(100)
);

INSERT INTO seed_login (username, plain_password, role, email) VALUES
  ('director1', 'Director123', 'DIRECTOR', 'director1@example.com'),
  ('director2', 'Director123', 'DIRECTOR', 'director2@example.com'),
  ('coach1', 'Coach123', 'COACH', 'coach1@example.com'),
  ('coach2', 'Coach123', 'COACH', 'coach2@example.com'),
  ('coach3', 'Coach123', 'COACH', 'coach3@example.com'),
  ('coach4', 'Coach123', 'COACH', 'coach4@example.com'),
  ('player1', 'Player123', 'PLAYER', 'player1@example.com'),
  ('player2', 'Player123', 'PLAYER', 'player2@example.com'),
  ('player3', 'Player123', 'PLAYER', 'player3@example.com'),
  ('player4', 'Player123', 'PLAYER', 'player4@example.com'),
  ('player5', 'Player123', 'PLAYER', 'player5@example.com'),
  ('player6', 'Player123', 'PLAYER', 'player6@example.com'),
  ('player7', 'Player123', 'PLAYER', 'player7@example.com'),
  ('player8', 'Player123', 'PLAYER', 'player8@example.com'),
  ('player9', 'Player123', 'PLAYER', 'player9@example.com'),
  ('player10', 'Player123', 'PLAYER', 'player10@example.com'),
  ('player11', 'Player123', 'PLAYER', 'player11@example.com'),
  ('player12', 'Player123', 'PLAYER', 'player12@example.com'),
  ('player13', 'Player123', 'PLAYER', 'player13@example.com'),
  ('player14', 'Player123', 'PLAYER', 'player14@example.com'),
  ('player15', 'Player123', 'PLAYER', 'player15@example.com'),
  ('player16', 'Player123', 'PLAYER', 'player16@example.com'),
  ('player17', 'Player123', 'PLAYER', 'player17@example.com'),
  ('player18', 'Player123', 'PLAYER', 'player18@example.com'),
  ('player19', 'Player123', 'PLAYER', 'player19@example.com'),
  ('player20', 'Player123', 'PLAYER', 'player20@example.com'),
  ('player21', 'Player123', 'PLAYER', 'player21@example.com'),
  ('player22', 'Player123', 'PLAYER', 'player22@example.com'),
  ('player23', 'Player123', 'PLAYER', 'player23@example.com'),
  ('player24', 'Player123', 'PLAYER', 'player24@example.com'),
  ('player25', 'Player123', 'PLAYER', 'player25@example.com'),
  ('player26', 'Player123', 'PLAYER', 'player26@example.com'),
  ('player27', 'Player123', 'PLAYER', 'player27@example.com'),
  ('player28', 'Player123', 'PLAYER', 'player28@example.com'),
  ('player29', 'Player123', 'PLAYER', 'player29@example.com'),
  ('player30', 'Player123', 'PLAYER', 'player30@example.com'),
  ('player31', 'Player123', 'PLAYER', 'player31@example.com'),
  ('player32', 'Player123', 'PLAYER', 'player32@example.com'),
  ('player33', 'Player123', 'PLAYER', 'player33@example.com'),
  ('player34', 'Player123', 'PLAYER', 'player34@example.com'),
  ('player35', 'Player123', 'PLAYER', 'player35@example.com'),
  ('player36', 'Player123', 'PLAYER', 'player36@example.com'),
  ('player37', 'Player123', 'PLAYER', 'player37@example.com'),
  ('player38', 'Player123', 'PLAYER', 'player38@example.com'),
  ('player39', 'Player123', 'PLAYER', 'player39@example.com'),
  ('player40', 'Player123', 'PLAYER', 'player40@example.com'),
  ('player41', 'Player123', 'PLAYER', 'player41@example.com'),
  ('player42', 'Player123', 'PLAYER', 'player42@example.com'),
  ('player43', 'Player123', 'PLAYER', 'player43@example.com'),
  ('player44', 'Player123', 'PLAYER', 'player44@example.com');

INSERT INTO UserAccount (username, password_salt, password_hash, role, email)
SELECT username, MD5(CONCAT(username, RAND())), REPEAT('0', 64), role, email
  FROM seed_login ORDER BY FIELD(role, 'DIRECTOR', 'COACH', 'PLAYER'), LENGTH(username), username;

UPDATE UserAccount u JOIN seed_login s ON s.username = u.username
   SET u.password_hash = SHA2(CONCAT(u.password_salt, s.plain_password), 256);

DROP TEMPORARY TABLE seed_login;

INSERT INTO Director (user_id, first_name, last_name, phone) VALUES
  ((SELECT user_id FROM UserAccount WHERE username = 'director1'), 'Sarah', 'Al Mansoori', '+971-50-111-2001'),
  ((SELECT user_id FROM UserAccount WHERE username = 'director2'), 'Omar', 'Haddad', '+971-50-111-2002');

INSERT INTO Coach (user_id, first_name, last_name, phone, years_experience) VALUES
  ((SELECT user_id FROM UserAccount WHERE username = 'coach1'), 'Daniel', 'Reyes', '+971-55-210-3001', 8),
  ((SELECT user_id FROM UserAccount WHERE username = 'coach2'), 'Aisha', 'Khan', '+971-55-210-3002', 5),
  ((SELECT user_id FROM UserAccount WHERE username = 'coach3'), 'Marco', 'Bianchi', '+971-55-210-3003', 11),
  ((SELECT user_id FROM UserAccount WHERE username = 'coach4'), 'Priya', 'Nair', '+971-55-210-3004', 3);

INSERT INTO Player (user_id, first_name, last_name, gamer_tag, date_of_birth, country) VALUES
  ((SELECT user_id FROM UserAccount WHERE username = 'player1'), 'Ahmed', 'Rahman', 'Mirage', '2005-02-02', 'UAE'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player2'), 'Lucas', 'Silva', 'Volt', '2002-09-01', 'Brazil'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player3'), 'Yusuf', 'Demir', 'Kestrel', '2001-10-17', 'Turkey'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player4'), 'Hana', 'Sato', 'Hanabi', '2001-12-21', 'Japan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player5'), 'Ethan', 'Walker', 'Rift', '2005-04-04', 'UK'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player6'), 'Mei', 'Chen', 'Lotus', '1998-02-26', 'China'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player7'), 'Karim', 'Nasser', 'Cipher', '2002-04-18', 'Egypt'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player8'), 'Sofia', 'Rossi', 'Sol', '2004-01-17', 'Italy'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player9'), 'Ravi', 'Patel', 'Raptor', '2005-02-02', 'India'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player10'), 'Noah', 'Fischer', 'Nox', '2004-01-15', 'Germany'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player11'), 'Layla', 'Haddad', 'Lynx', '2002-12-23', 'Jordan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player12'), 'Jonas', 'Berg', 'Frost', '1999-03-19', 'Sweden'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player13'), 'Fatima', 'Zahra', 'Saffron', '2003-11-17', 'Pakistan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player14'), 'Arjun', 'Menon', 'Blitz', '2005-09-13', 'India'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player15'), 'Elena', 'Petrova', 'Vega', '2004-11-06', 'Russia'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player16'), 'Hamza', 'Qureshi', 'Havoc', '2003-10-15', 'Pakistan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player17'), 'Chloe', 'Martin', 'Pixel', '2000-06-01', 'France'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player18'), 'Tariq', 'Aziz', 'Talon', '1996-05-01', 'Saudi Arabia'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player19'), 'Leo', 'Dubois', 'Echo', '2003-07-18', 'France'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player20'), 'Zara', 'Malik', 'Zephyr', '2005-11-28', 'UAE'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player21'), 'Ibrahim', 'Saleh', 'Ember', '2001-09-26', 'Oman'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player22'), 'Nina', 'Novak', 'Nyx', '2004-03-07', 'Czechia'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player23'), 'Kenji', 'Tanaka', 'Kaze', '2000-03-14', 'Japan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player24'), 'Amira', 'Farouk', 'Aurora', '2002-08-01', 'Egypt'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player25'), 'Mateo', 'Lopez', 'Fuego', '2001-09-13', 'Spain'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player26'), 'Sara', 'Hassan', 'Sable', '1999-03-22', 'UAE'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player27'), 'Dmitri', 'Ivanov', 'Tundra', '2006-02-16', 'Russia'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player28'), 'Aditi', 'Sharma', 'Comet', '2004-09-23', 'India'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player29'), 'Bilal', 'Siddiqui', 'Onyx', '1997-05-01', 'Pakistan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player30'), 'Emma', 'Brown', 'Quill', '1999-11-01', 'USA'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player31'), 'Rohan', 'Kapoor', 'Rook', '1998-04-21', 'India'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player32'), 'Mariam', 'Khalil', 'Mirai', '2000-02-28', 'Lebanon'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player33'), 'Felix', 'Weber', 'Flux', '2005-05-25', 'Germany'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player34'), 'Yara', 'Odeh', 'Dune', '1999-01-21', 'Jordan'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player35'), 'Hugo', 'Moreau', 'Hex', '2003-08-28', 'France'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player36'), 'Salma', 'Darwish', 'Saber', '1998-03-16', 'Syria'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player37'), 'Viktor', 'Kovac', 'Viper', '2000-12-03', 'Croatia'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player38'), 'Ananya', 'Iyer', 'Orbit', '1997-08-23', 'India'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player39'), 'Ali', 'Raza', 'Ace', '1999-09-07', 'UAE'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player40'), 'Isabel', 'Garcia', 'Nova', '2005-04-25', 'Mexico'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player41'), 'Samir', 'Yousef', 'Drift', '2002-07-27', 'Bahrain'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player42'), 'Lina', 'Mansour', 'Halo', '2006-03-08', 'Kuwait'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player43'), 'Theo', 'Klein', 'Jolt', '2001-03-01', 'Germany'),
  ((SELECT user_id FROM UserAccount WHERE username = 'player44'), 'Nour', 'Jaber', 'Lumen', '1999-08-20', 'Qatar');

INSERT INTO Game (game_name, genre, publisher, players_per_team) VALUES
  ('Valorant',          'Tactical Shooter', 'Riot Games', 5),
  ('League of Legends', 'MOBA',             'Riot Games', 5),
  ('Rocket League',     'Sports',           'Psyonix',    3);

INSERT INTO Team (team_name, game_id, coach_id, region, founded_date) VALUES
  ('Desert Vipers',     1, 1, 'MENA',   '2023-02-10'),
  ('Falcon Five',       1, 2, 'MENA',   '2022-09-01'),
  ('Neon Nomads',       1, 3, 'Europe', '2024-01-15'),
  ('Sandstorm Esports', 1, 4, 'MENA',   '2021-06-20'),
  ('Oasis Legends',     2, 1, 'MENA',   '2023-08-05'),
  ('Crimson Dhows',     2, 2, 'MENA',   '2024-03-12'),
  ('Arctic Mages',      2, 3, 'Europe', '2022-11-30'),
  ('Golden Scarabs',    2, 4, 'MENA',   '2025-01-08');

-- Many-to-many over time: player41 LEFT Falcon Five (team 2) on 2026-03-01 and then joined Desert Vipers (team 1).
-- player1 is on TWO teams at the same time (Desert Vipers and Oasis Legends).
-- players 42, 43, 44 are free agents (no team) so 'Add Player To Team' can be demonstrated.
INSERT INTO TeamMembership (team_id, player_id, join_date, leave_date, team_role) VALUES
  (1, 1, '2025-06-20', NULL, 'CAPTAIN'),
  (1, 2, '2025-04-01', NULL, 'STARTER'),
  (1, 3, '2025-02-13', NULL, 'STARTER'),
  (1, 4, '2025-02-10', NULL, 'STARTER'),
  (1, 5, '2025-06-11', NULL, 'STARTER'),
  (2, 6, '2025-05-23', NULL, 'CAPTAIN'),
  (2, 7, '2025-03-19', NULL, 'STARTER'),
  (2, 8, '2025-03-10', NULL, 'STARTER'),
  (2, 9, '2025-06-02', NULL, 'STARTER'),
  (2, 10, '2025-05-10', NULL, 'STARTER'),
  (3, 11, '2025-04-03', NULL, 'CAPTAIN'),
  (3, 12, '2025-06-17', NULL, 'STARTER'),
  (3, 13, '2025-04-19', NULL, 'STARTER'),
  (3, 14, '2025-06-27', NULL, 'STARTER'),
  (3, 15, '2025-05-16', NULL, 'STARTER'),
  (4, 16, '2025-03-21', NULL, 'CAPTAIN'),
  (4, 17, '2025-02-15', NULL, 'STARTER'),
  (4, 18, '2025-05-04', NULL, 'STARTER'),
  (4, 19, '2025-06-05', NULL, 'STARTER'),
  (4, 20, '2025-06-22', NULL, 'STARTER'),
  (5, 21, '2026-04-07', NULL, 'CAPTAIN'),
  (5, 22, '2026-03-01', NULL, 'STARTER'),
  (5, 23, '2026-01-13', NULL, 'STARTER'),
  (5, 24, '2026-02-19', NULL, 'STARTER'),
  (5, 25, '2026-06-11', NULL, 'STARTER'),
  (6, 26, '2026-04-13', NULL, 'CAPTAIN'),
  (6, 27, '2026-02-22', NULL, 'STARTER'),
  (6, 28, '2026-06-27', NULL, 'STARTER'),
  (6, 29, '2026-02-28', NULL, 'STARTER'),
  (6, 30, '2026-01-05', NULL, 'STARTER'),
  (7, 31, '2026-03-19', NULL, 'CAPTAIN'),
  (7, 32, '2026-06-03', NULL, 'STARTER'),
  (7, 33, '2026-06-17', NULL, 'STARTER'),
  (7, 34, '2026-01-26', NULL, 'STARTER'),
  (7, 35, '2026-02-24', NULL, 'STARTER'),
  (8, 36, '2026-03-09', NULL, 'CAPTAIN'),
  (8, 37, '2026-06-13', NULL, 'STARTER'),
  (8, 38, '2026-01-28', NULL, 'STARTER'),
  (8, 39, '2026-01-22', NULL, 'STARTER'),
  (8, 40, '2026-01-12', NULL, 'STARTER'),
  (2, 41, '2024-05-01', '2026-03-01', 'STARTER'),
  (1, 41, '2026-03-15', NULL, 'SUBSTITUTE'),
  (5, 1, '2026-02-01', NULL, 'SUBSTITUTE');

INSERT INTO Tournament (tournament_name, game_id, director_id, start_date, end_date, location, prize_pool, max_teams, status) VALUES
  ('Dubai Valorant Winter Cup 2026',       1, 1, '2026-01-15', '2026-01-17', 'Dubai Media City Arena', 25000.00, 8, 'COMPLETED'),
  ('Gulf League of Legends Series 2026',   2, 2, '2026-11-05', '2026-11-25', 'Online',                 10000.00, 8, 'ONGOING'),
  ('RIT Dubai Valorant Spring Open 2027',  1, 1, '2027-02-10', '2027-02-12', 'RIT Dubai Campus',        5000.00, 8, 'UPCOMING'),
  ('League of Legends Spring Clash 2027',  2, 2, '2027-03-05', '2027-03-07', 'Abu Dhabi Esports Hub',   7500.00, 4, 'UPCOMING'),
  ('Rocket League Desert Showdown 2027',   3, 1, '2027-04-02', '2027-04-03', 'Sharjah Expo Centre',     3000.00, 8, 'UPCOMING');

INSERT INTO TournamentRegistration (tournament_id, team_id, registration_date, seed_number) VALUES
  (1, 1, '2025-12-01', 1), (1, 2, '2025-12-02', 2), (1, 3, '2025-12-04', 3), (1, 4, '2025-12-05', 4),
  (2, 5, '2026-10-10', 1), (2, 6, '2026-10-11', 2), (2, 7, '2026-10-12', 3), (2, 8, '2026-10-15', 4),
  (3, 1, '2026-11-01', 1), (3, 3, '2026-11-03', 2),
  (4, 5, '2026-11-02', 1);

INSERT INTO TournamentMatch (tournament_id, home_team_id, away_team_id, round_name, scheduled_at, venue, home_score, away_score, status) VALUES
  (1, 1, 2, 'Round 1', '2026-01-15 14:00', 'Dubai Media City Arena - Stage A', 13, 9, 'COMPLETED'),
  (1, 3, 4, 'Round 1', '2026-01-15 17:00', 'Dubai Media City Arena - Stage A', 11, 13, 'COMPLETED'),
  (1, 1, 3, 'Round 2', '2026-01-16 14:00', 'Dubai Media City Arena - Stage A', 13, 7, 'COMPLETED'),
  (1, 2, 4, 'Round 2', '2026-01-16 17:00', 'Dubai Media City Arena - Stage A', 13, 10, 'COMPLETED'),
  (1, 1, 4, 'Round 3', '2026-01-17 14:00', 'Dubai Media City Arena - Stage A', 10, 13, 'COMPLETED'),
  (1, 2, 3, 'Round 3', '2026-01-17 17:00', 'Dubai Media City Arena - Stage A', 13, 11, 'COMPLETED'),
  (2, 5, 6, 'Week 1', '2026-11-05 19:00', 'Online', 2, 1, 'COMPLETED'),
  (2, 7, 8, 'Week 1', '2026-11-06 19:00', 'Online', 0, 2, 'COMPLETED'),
  (2, 5, 7, 'Week 2', '2026-11-09 19:00', 'Online', 2, 0, 'COMPLETED'),
  (2, 6, 8, 'Week 2', '2026-11-10 19:00', 'Online', 1, 2, 'COMPLETED'),
  (2, 5, 8, 'Week 3', '2026-11-19 19:00', 'Online', NULL, NULL, 'SCHEDULED'),
  (2, 6, 7, 'Week 3', '2026-11-20 19:00', 'Online', NULL, NULL, 'SCHEDULED'),
  (3, 1, 3, 'Group A', '2027-02-10 15:00', 'RIT Dubai Campus - Auditorium', NULL, NULL, 'SCHEDULED');

INSERT INTO PlayerMatchStat (match_id, player_id, kills, deaths, assists) VALUES
  (1, 1, 12, 14, 11), (1, 2, 15, 14, 4), (1, 3, 17, 11, 7), (1, 4, 21, 12, 5), (1, 5, 24, 12, 5),
  (1, 6, 20, 17, 7), (1, 7, 11, 17, 10), (1, 8, 14, 13, 12), (1, 9, 11, 15, 10), (1, 10, 23, 15, 6),
  (2, 11, 26, 16, 5), (2, 12, 24, 11, 11), (2, 13, 20, 19, 2), (2, 14, 17, 20, 6), (2, 15, 16, 10, 5),
  (2, 16, 20, 13, 11), (2, 17, 9, 19, 11), (2, 18, 17, 10, 10), (2, 19, 18, 19, 3), (2, 20, 23, 19, 11),
  (3, 1, 25, 10, 7), (3, 2, 19, 18, 3), (3, 3, 16, 21, 9), (3, 4, 16, 10, 8), (3, 5, 10, 11, 9),
  (3, 11, 12, 15, 6), (3, 12, 25, 20, 4), (3, 13, 20, 11, 2), (3, 14, 14, 10, 12), (3, 15, 18, 13, 4),
  (4, 6, 18, 9, 8), (4, 7, 15, 12, 5), (4, 8, 13, 17, 6), (4, 9, 15, 21, 12), (4, 10, 17, 14, 10),
  (4, 16, 11, 21, 10), (4, 17, 14, 21, 9), (4, 18, 20, 18, 11), (4, 19, 27, 20, 2), (4, 20, 27, 8, 6),
  (5, 1, 19, 12, 4), (5, 2, 27, 10, 10), (5, 3, 19, 20, 8), (5, 4, 10, 12, 3), (5, 5, 27, 18, 2),
  (5, 16, 26, 8, 6), (5, 17, 19, 21, 2), (5, 18, 9, 19, 2), (5, 19, 17, 8, 8), (5, 20, 26, 12, 4),
  (6, 6, 23, 16, 5), (6, 7, 21, 17, 2), (6, 8, 23, 20, 2), (6, 9, 24, 19, 2), (6, 10, 20, 8, 3),
  (6, 11, 23, 12, 2), (6, 12, 13, 8, 9), (6, 13, 16, 8, 5), (6, 14, 16, 9, 8), (6, 15, 16, 17, 6),
  (7, 21, 7, 2, 17), (7, 22, 8, 2, 18), (7, 23, 6, 2, 12), (7, 24, 9, 11, 10), (7, 25, 13, 1, 8),
  (7, 26, 14, 3, 22), (7, 27, 11, 10, 16), (7, 28, 2, 6, 7), (7, 29, 9, 2, 12), (7, 30, 10, 12, 17),
  (8, 31, 14, 4, 14), (8, 32, 9, 7, 5), (8, 33, 8, 1, 17), (8, 34, 13, 9, 15), (8, 35, 9, 3, 5),
  (8, 36, 8, 8, 12), (8, 37, 12, 2, 24), (8, 38, 9, 11, 23), (8, 39, 2, 7, 12), (8, 40, 3, 2, 19),
  (9, 21, 7, 9, 18), (9, 22, 13, 8, 14), (9, 23, 2, 4, 8), (9, 24, 2, 1, 13), (9, 25, 2, 8, 25),
  (9, 31, 11, 11, 13), (9, 32, 7, 9, 20), (9, 33, 4, 11, 23), (9, 34, 2, 4, 22), (9, 35, 8, 3, 10),
  (10, 26, 11, 5, 23), (10, 27, 13, 11, 13), (10, 28, 13, 6, 25), (10, 29, 4, 4, 11), (10, 30, 9, 6, 15),
  (10, 36, 2, 7, 12), (10, 37, 3, 12, 11), (10, 38, 8, 3, 16), (10, 39, 9, 9, 9), (10, 40, 12, 8, 8);

-- Join requests: two are waiting for coach1 (Daniel Reyes), one was declined by coach2.
-- player42 (Halo) has no request yet, so "Ask to join" can be shown from the start.
INSERT INTO JoinRequest (team_id, player_id, requested_role, message, status, requested_at, answered_at) VALUES
  (1, 44, 'SUBSTITUTE', 'I can play every weekend and I know all the maps.', 'PENDING',
   '2026-11-10 18:30', NULL),
  (5, 43, 'STARTER', 'I played League of Legends at school for 3 years.', 'PENDING',
   '2026-11-12 20:15', NULL),
  (2, 44, 'STARTER', NULL, 'DECLINED', '2026-10-01 12:00', '2026-10-03 09:00');

-- ---------------------------------------------------------------------
-- 5. QUICK CHECK - row counts (you should see 12 rows, none with 0)
-- ---------------------------------------------------------------------
SELECT 'UserAccount' AS table_name, COUNT(*) AS row_count FROM UserAccount
UNION ALL SELECT 'Director',               COUNT(*) FROM Director
UNION ALL SELECT 'Coach',                  COUNT(*) FROM Coach
UNION ALL SELECT 'Player',                 COUNT(*) FROM Player
UNION ALL SELECT 'Game',                   COUNT(*) FROM Game
UNION ALL SELECT 'Team',                   COUNT(*) FROM Team
UNION ALL SELECT 'TeamMembership',         COUNT(*) FROM TeamMembership
UNION ALL SELECT 'Tournament',             COUNT(*) FROM Tournament
UNION ALL SELECT 'TournamentRegistration', COUNT(*) FROM TournamentRegistration
UNION ALL SELECT 'TournamentMatch',        COUNT(*) FROM TournamentMatch
UNION ALL SELECT 'PlayerMatchStat',        COUNT(*) FROM PlayerMatchStat
UNION ALL SELECT 'JoinRequest',            COUNT(*) FROM JoinRequest;
