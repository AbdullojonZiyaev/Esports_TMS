// DataLayer
// Esports Tournament Management System
// ISTE 330 - Group 1
//
// Everything that talks to the database is in this class:
//   - connecting / disconnecting
//   - password hashing (salted SHA-256)
//   - login, CRUD, search (PreparedStatement)
//   - the 5 stored procedures (CallableStatement)
//   - join requests (a player asks, the coach answers)
//   - the 5 reports (ResultSet + ResultSetMetaData)
//
// Query results are returned as a list of String rows. Row 0 holds the column names.

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

public class DataLayer {

    // attributes
    Connection conn;

    final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    final String URL = "jdbc:mysql://localhost:3306/esports_tms"
                     + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // roles stored in UserAccount.role
    public static final String DIRECTOR = "DIRECTOR";
    public static final String COACH = "COACH";
    public static final String PLAYER = "PLAYER";

    private final SecureRandom random = new SecureRandom();

    // empty constructor
    public DataLayer() {
    }

    // =====================================================================
    // CONNECTION
    // =====================================================================

    // attempts to connect to the database, returns true if the connection works
    public boolean connect(String userName, String password) {
        conn = null;
        try {
            try {
                // normal way: the driver is already on the classpath (IDE or -cp)
                Class.forName(DEFAULT_DRIVER);
                conn = DriverManager.getConnection(URL, userName, password);
            } catch (ClassNotFoundException cnfe) {
                // easy way: load mysql-connector-j-*.jar from the program folder,
                // so "java PresentationLayer" works without -cp
                Driver driver = driverFromJar();
                if (driver == null) {
                    System.out.println("ERROR: MySQL JDBC driver not found.");
                    System.out.println("Keep mysql-connector-j-9.0.0.jar in the same folder as the program.");
                    return false;
                }
                Properties login = new Properties();
                login.setProperty("user", userName);
                login.setProperty("password", password);
                conn = driver.connect(URL, login);
            }
            System.out.println("\nConnected to database esports_tms.\n");
        } catch (SQLException sqle) {
            System.out.println("ERROR connecting to MySQL -> " + sqle.getMessage());
            if (sqle.getMessage() != null && sqle.getMessage().contains("Unknown database")) {
                System.out.println("Run esports_tms.sql in MySQL first - it creates the database.");
            }
        }
        return (conn != null);
    }

    // looks for mysql-connector-j-*.jar in the current folder and in the folder of DataLayer.class
    private Driver driverFromJar() {
        ArrayList<File> folders = new ArrayList<>();
        folders.add(new File("."));
        try {
            File classFolder = new File(DataLayer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            folders.add(classFolder);
            folders.add(classFolder.getParentFile());            // IDEs put classes in a bin/ or out/ folder
        } catch (Exception e) {
            // folder of the class file unknown - the current folder is enough
        }
        for (File folder : folders) {
            File[] jars = folder.listFiles((dir, name) -> name.startsWith("mysql-connector") && name.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                try {
                    URLClassLoader loader = new URLClassLoader(new URL[] {jars[0].toURI().toURL()},
                            DataLayer.class.getClassLoader());
                    return (Driver) Class.forName(DEFAULT_DRIVER, true, loader).getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    System.out.println("Could not load " + jars[0].getName() + " -> " + e);
                }
            }
        }
        return null;
    }

    // closes the connection
    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException sqle) {
            System.out.println("ERROR IN METHOD close() -> " + sqle);
        }
    }

    // =====================================================================
    // PASSWORD HASHING
    // stored hash = SHA-256(salt + password) as 64 hex characters.
    // Same as MySQL SHA2(CONCAT(salt, password), 256) used in esports_tms.sql.
    // =====================================================================

    // new random salt: 16 random bytes = 32 hex characters
    private String newSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return toHex(bytes);
    }

    private String hash(String salt, String plainPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest((salt + plainPassword).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    // runs a filled-in PreparedStatement and returns all rows as text.
    // row 0 = column names (from ResultSetMetaData)
    private ArrayList<String[]> readTable(PreparedStatement ps) throws SQLException {
        ArrayList<String[]> table = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            String[] headers = new String[columns];
            for (int c = 1; c <= columns; c++) {
                headers[c - 1] = meta.getColumnLabel(c);
            }
            table.add(headers);
            while (rs.next()) {
                String[] row = new String[columns];
                for (int c = 1; c <= columns; c++) {
                    String value = rs.getString(c);
                    if (value == null) {
                        value = "-";
                    } else if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
                        value = value.substring(0, 16);   // drop the seconds
                    }
                    row[c - 1] = value;
                }
                table.add(row);
            }
        }
        return table;
    }

    // reads the two OUT parameters every stored procedure has: p_success, p_message
    private String procedureResult(CallableStatement cs, int successIndex) throws SQLException {
        boolean ok = cs.getInt(successIndex) == 1;
        return (ok ? "SUCCESS: " : "NOT DONE: ") + cs.getString(successIndex + 1);
    }

    // =====================================================================
    // LOGIN AND ACCOUNTS
    // =====================================================================

    // checks username + password.
    // returns {userId, role, profileId, fullName} or null if the login is wrong.
    // profileId = director_id, coach_id or player_id depending on the role.
    public String[] login(String username, String plainPassword) throws SQLException {
        String sql = "SELECT user_id, password_hash, password_salt, role, is_active "
                   + "FROM UserAccount WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;                                       // no such username
                }
                String typedHash = hash(rs.getString("password_salt"), plainPassword);
                if (!MessageDigest.isEqual(typedHash.getBytes(StandardCharsets.US_ASCII),
                        rs.getString("password_hash").toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                    return null;                                       // wrong password
                }
                if (rs.getInt("is_active") != 1) {
                    throw new SQLException("This account has been disabled.");
                }
                int userId = rs.getInt("user_id");
                String role = rs.getString("role");

                // find the Director / Coach / Player row of this account
                // (table names come from this code, never from the user)
                String table = DIRECTOR.equals(role) ? "Director" : COACH.equals(role) ? "Coach" : "Player";
                String idColumn = table.toLowerCase() + "_id";
                String profileSql = "SELECT " + idColumn + ", first_name, last_name FROM " + table
                                  + " WHERE user_id = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(profileSql)) {
                    ps2.setInt(1, userId);
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (!rs2.next()) {
                            throw new SQLException("Account " + username + " has no " + table + " profile.");
                        }
                        return new String[] {String.valueOf(userId), role, String.valueOf(rs2.getInt(1)),
                                rs2.getString("first_name") + " " + rs2.getString("last_name")};
                    }
                }
            }
        }
    }

    // true if a column (username or email) of UserAccount already has this value
    public boolean accountValueExists(String column, String value) throws SQLException {
        String col = column.equals("email") ? "email" : "username";     // never taken from the user
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM UserAccount WHERE " + col + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // true if a player already uses this gamer tag
    public boolean gamerTagExists(String gamerTag) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Player WHERE gamer_tag = ?")) {
            ps.setString(1, gamerTag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // CREATE: login account + Director/Coach/Player profile in ONE transaction.
    // extra1/extra2/extra3: DIRECTOR = phone | COACH = phone, years | PLAYER = gamer tag, birth date, country
    // returns the new user_id
    public int createAccount(String username, String plainPassword, String role, String email,
                             String firstName, String lastName,
                             String extra1, String extra2, String extra3) throws SQLException {
        String salt = newSalt();
        conn.setAutoCommit(false);
        try {
            int userId;
            String userSql = "INSERT INTO UserAccount (username, password_hash, password_salt, role, email) "
                           + "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, hash(salt, plainPassword));
                ps.setString(3, salt);
                ps.setString(4, role);
                ps.setString(5, email);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    userId = keys.getInt(1);
                }
            }

            String profileSql;
            if (DIRECTOR.equals(role)) {
                profileSql = "INSERT INTO Director (user_id, first_name, last_name, phone) VALUES (?, ?, ?, ?)";
            } else if (COACH.equals(role)) {
                profileSql = "INSERT INTO Coach (user_id, first_name, last_name, phone, years_experience) "
                           + "VALUES (?, ?, ?, ?, ?)";
            } else {
                profileSql = "INSERT INTO Player (user_id, first_name, last_name, gamer_tag, date_of_birth, country) "
                           + "VALUES (?, ?, ?, ?, ?, ?)";
            }
            try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
                ps.setInt(1, userId);
                ps.setString(2, firstName);
                ps.setString(3, lastName);
                ps.setString(4, extra1);
                if (COACH.equals(role)) {
                    ps.setInt(5, Integer.parseInt(extra2));
                } else if (PLAYER.equals(role)) {
                    ps.setDate(5, java.sql.Date.valueOf(extra2));
                    ps.setString(6, extra3);
                }
                ps.executeUpdate();
            }
            conn.commit();
            return userId;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();                         // neither row is saved
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // UPDATE: new salt + new hash
    public boolean changePassword(int userId, String newPlainPassword) throws SQLException {
        String salt = newSalt();
        String sql = "UPDATE UserAccount SET password_salt = ?, password_hash = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, salt);
            ps.setString(2, hash(salt, newPlainPassword));
            ps.setInt(3, userId);
            return ps.executeUpdate() == 1;
        }
    }

    // =====================================================================
    // TOURNAMENTS (CRUD)
    // =====================================================================

    private final String TOURNAMENT_LIST =
            "SELECT t.tournament_id AS ID, t.tournament_name AS Tournament, g.game_name AS Game, "
          + "t.start_date AS Starts, t.end_date AS Ends, t.location AS Location, t.prize_pool AS `Prize (AED)`, "
          + "(SELECT COUNT(*) FROM TournamentRegistration r WHERE r.tournament_id = t.tournament_id) AS Teams, "
          + "t.max_teams AS `Max teams`, t.status AS Status "
          + "FROM Tournament t JOIN Game g ON g.game_id = t.game_id ";

    // READ all
    public ArrayList<String[]> listTournaments() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TOURNAMENT_LIST + "ORDER BY t.start_date")) {
            return readTable(ps);
        }
    }

    // SEARCH by name
    public ArrayList<String[]> searchTournaments(String text) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                TOURNAMENT_LIST + "WHERE t.tournament_name LIKE ? ORDER BY t.start_date")) {
            ps.setString(1, "%" + text + "%");
            return readTable(ps);
        }
    }

    // READ one: {name, start, end, location, prize, maxTeams, status} or null
    public String[] getTournament(int id) throws SQLException {
        String sql = "SELECT tournament_name, start_date, end_date, location, prize_pool, max_teams, status "
                   + "FROM Tournament WHERE tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String[] t = new String[7];
                for (int c = 0; c < 7; c++) {
                    t[c] = rs.getString(c + 1);
                }
                return t;
            }
        }
    }

    // CREATE, returns the new tournament_id
    public int addTournament(String name, int gameId, int directorId, String start, String end,
                             String location, BigDecimal prize, int maxTeams) throws SQLException {
        String sql = "INSERT INTO Tournament (tournament_name, game_id, director_id, start_date, end_date, "
                   + "location, prize_pool, max_teams, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UPCOMING')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, gameId);
            ps.setInt(3, directorId);
            ps.setDate(4, java.sql.Date.valueOf(start));
            ps.setDate(5, java.sql.Date.valueOf(end));
            ps.setString(6, location);
            ps.setBigDecimal(7, prize);
            ps.setInt(8, maxTeams);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // UPDATE
    public boolean updateTournament(int id, String name, String start, String end, String location,
                                    BigDecimal prize, int maxTeams, String status) throws SQLException {
        String sql = "UPDATE Tournament SET tournament_name = ?, start_date = ?, end_date = ?, location = ?, "
                   + "prize_pool = ?, max_teams = ?, status = ? WHERE tournament_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDate(2, java.sql.Date.valueOf(start));
            ps.setDate(3, java.sql.Date.valueOf(end));
            ps.setString(4, location);
            ps.setBigDecimal(5, prize);
            ps.setInt(6, maxTeams);
            ps.setString(7, status);
            ps.setInt(8, id);
            return ps.executeUpdate() == 1;
        }
    }

    // DELETE (registrations, matches and stats are removed by ON DELETE CASCADE)
    public boolean deleteTournament(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Tournament WHERE tournament_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    public ArrayList<String[]> listGames() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT game_id AS ID, game_name AS Game, genre AS Genre, players_per_team AS `Players/team` "
              + "FROM Game ORDER BY game_id")) {
            return readTable(ps);
        }
    }

    // STORED PROCEDURE 1 (CallableStatement): register a team for a tournament
    public String registerTeam(int tournamentId, int teamId) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{CALL sp_RegisterTeamForTournament(?, ?, ?, ?)}")) {
            cs.setInt(1, tournamentId);
            cs.setInt(2, teamId);
            cs.registerOutParameter(3, Types.TINYINT);
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.execute();
            return procedureResult(cs, 3);
        }
    }

    // =====================================================================
    // TEAMS AND ROSTERS (Player <-> Team many-to-many)
    // =====================================================================

    private final String TEAM_LIST =
            "SELECT t.team_id AS ID, t.team_name AS Team, g.game_name AS Game, "
          + "CONCAT(c.first_name, ' ', c.last_name) AS Coach, t.region AS Region, "
          + "(SELECT COUNT(*) FROM TeamMembership m WHERE m.team_id = t.team_id AND m.leave_date IS NULL) AS Players "
          + "FROM Team t JOIN Game g ON g.game_id = t.game_id JOIN Coach c ON c.coach_id = t.coach_id ";

    public ArrayList<String[]> listTeams() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TEAM_LIST + "ORDER BY t.team_id")) {
            return readTable(ps);
        }
    }

    public ArrayList<String[]> teamsOfCoach(int coachId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TEAM_LIST + "WHERE t.coach_id = ? ORDER BY t.team_id")) {
            ps.setInt(1, coachId);
            return readTable(ps);
        }
    }

    public ArrayList<String[]> searchTeams(String text) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TEAM_LIST + "WHERE t.team_name LIKE ? ORDER BY t.team_name")) {
            ps.setString(1, "%" + text + "%");
            return readTable(ps);
        }
    }

    // security check: a coach may only change their own teams
    public boolean coachOwnsTeam(int coachId, int teamId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Team WHERE team_id = ? AND coach_id = ?")) {
            ps.setInt(1, teamId);
            ps.setInt(2, coachId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // players who are not currently on this team
    public ArrayList<String[]> playersNotOnTeam(int teamId) throws SQLException {
        String sql = "SELECT p.player_id AS ID, p.gamer_tag AS `Gamer tag`, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, p.country AS Country, "
                   + "(SELECT COUNT(*) FROM TeamMembership m2 WHERE m2.player_id = p.player_id "
                   + "  AND m2.leave_date IS NULL) AS `Current teams` "
                   + "FROM Player p WHERE p.player_id NOT IN "
                   + "(SELECT m.player_id FROM TeamMembership m WHERE m.team_id = ? AND m.leave_date IS NULL) "
                   + "ORDER BY `Current teams`, p.player_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            return readTable(ps);
        }
    }

    // STORED PROCEDURE 2 (CallableStatement): add a player to a team
    public String addPlayerToTeam(int teamId, int playerId, String role) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{CALL sp_AddPlayerToTeam(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, teamId);
            cs.setInt(2, playerId);
            cs.setString(3, role);
            cs.registerOutParameter(4, Types.TINYINT);
            cs.registerOutParameter(5, Types.VARCHAR);
            cs.execute();
            return procedureResult(cs, 4);
        }
    }

    // UPDATE: the player leaves the team; the row is kept as history
    public boolean removePlayerFromTeam(int teamId, int playerId) throws SQLException {
        String sql = "UPDATE TeamMembership SET leave_date = CURRENT_DATE "
                   + "WHERE team_id = ? AND player_id = ? AND leave_date IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, playerId);
            return ps.executeUpdate() == 1;
        }
    }

    // =====================================================================
    // MATCHES
    // =====================================================================

    public ArrayList<String[]> scheduledMatches() throws SQLException {
        String sql = "SELECT match_id AS ID, tournament_name AS Tournament, round_name AS Round, "
                   + "scheduled_at AS `Date/time`, home_team AS Home, away_team AS Away "
                   + "FROM vw_MatchSchedule WHERE status = 'SCHEDULED' ORDER BY scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return readTable(ps);
        }
    }

    private boolean isRegistered(int tournamentId, int teamId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM TournamentRegistration WHERE tournament_id = ? AND team_id = ?")) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // CREATE a match (dateTime = "yyyy-mm-dd hh:mm"), returns the new match_id
    public int scheduleMatch(int tournamentId, int homeTeamId, int awayTeamId, String round,
                             String dateTime, String venue) throws SQLException {
        if (homeTeamId == awayTeamId) {
            throw new SQLException("A team cannot play against itself.");
        }
        if (!isRegistered(tournamentId, homeTeamId) || !isRegistered(tournamentId, awayTeamId)) {
            throw new SQLException("Both teams must be registered in this tournament.");
        }
        String sql = "INSERT INTO TournamentMatch (tournament_id, home_team_id, away_team_id, round_name, "
                   + "scheduled_at, venue) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, homeTeamId);
            ps.setInt(3, awayTeamId);
            ps.setString(4, round);
            ps.setString(5, dateTime + ":00");      // text, so no time-zone shift
            ps.setString(6, venue);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // STORED PROCEDURE 3 (CallableStatement): record a match result
    public String recordMatchResult(int matchId, int homeScore, int awayScore) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{CALL sp_RecordMatchResult(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, matchId);
            cs.setInt(2, homeScore);
            cs.setInt(3, awayScore);
            cs.registerOutParameter(4, Types.TINYINT);
            cs.registerOutParameter(5, Types.VARCHAR);
            cs.execute();
            return procedureResult(cs, 4);
        }
    }

    // =====================================================================
    // PLAYERS (a player only sees their own data)
    // =====================================================================

    public ArrayList<String[]> playerProfile(int playerId) throws SQLException {
        String sql = "SELECT p.gamer_tag AS `Gamer tag`, p.first_name AS `First name`, p.last_name AS `Last name`, "
                   + "p.date_of_birth AS Born, p.country AS Country, u.email AS Email, u.username AS Username "
                   + "FROM Player p JOIN UserAccount u ON u.user_id = p.user_id WHERE p.player_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    // UPDATE email (UserAccount) and country (Player) in one transaction
    public void updatePlayerContact(int playerId, int userId, String email, String country) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps1 = conn.prepareStatement("UPDATE UserAccount SET email = ? WHERE user_id = ?");
             PreparedStatement ps2 = conn.prepareStatement("UPDATE Player SET country = ? WHERE player_id = ?")) {
            ps1.setString(1, email);
            ps1.setInt(2, userId);
            ps1.executeUpdate();
            ps2.setString(1, country);
            ps2.setInt(2, playerId);
            ps2.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // all teams the player has been on (current and past)
    public ArrayList<String[]> playerTeams(int playerId) throws SQLException {
        String sql = "SELECT t.team_id AS `Team ID`, t.team_name AS Team, g.game_name AS Game, m.team_role AS Role, "
                   + "m.join_date AS Joined, COALESCE(m.leave_date, 'current') AS `Left` "
                   + "FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id "
                   + "JOIN Game g ON g.game_id = t.game_id WHERE m.player_id = ? "
                   + "ORDER BY m.leave_date IS NULL DESC, m.join_date DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    // matches of every team the player is currently on
    public ArrayList<String[]> playerMatches(int playerId) throws SQLException {
        String sql = "SELECT s.match_id AS `Match ID`, s.scheduled_at AS `Date/time`, s.tournament_name AS Tournament, "
                   + "s.round_name AS Round, s.home_team AS Home, s.away_team AS Away, "
                   + "CONCAT(COALESCE(s.home_score, '-'), ' : ', COALESCE(s.away_score, '-')) AS Score, "
                   + "COALESCE(s.winner, '') AS Winner, s.status AS Status "
                   + "FROM vw_MatchSchedule s WHERE s.home_team_id IN "
                   + "  (SELECT team_id FROM TeamMembership WHERE player_id = ? AND leave_date IS NULL) "
                   + "OR s.away_team_id IN "
                   + "  (SELECT team_id FROM TeamMembership WHERE player_id = ? AND leave_date IS NULL) "
                   + "ORDER BY s.scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setInt(2, playerId);
            return readTable(ps);
        }
    }

    public ArrayList<String[]> playerStatsPerMatch(int playerId) throws SQLException {
        String sql = "SELECT x.match_id AS `Match ID`, s.scheduled_at AS Date, s.tournament_name AS Tournament, "
                   + "CONCAT(s.home_team, ' vs ', s.away_team) AS `Match`, "
                   + "x.kills AS Kills, x.deaths AS Deaths, x.assists AS Assists "
                   + "FROM PlayerMatchStat x JOIN vw_MatchSchedule s ON s.match_id = x.match_id "
                   + "WHERE x.player_id = ? ORDER BY s.scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    public ArrayList<String[]> searchPlayers(String text) throws SQLException {
        String sql = "SELECT player_id AS ID, gamer_tag AS `Gamer tag`, CONCAT(first_name, ' ', last_name) AS Name, "
                   + "country AS Country FROM Player "
                   + "WHERE gamer_tag LIKE ? OR first_name LIKE ? OR last_name LIKE ? ORDER BY gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + text + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            return readTable(ps);
        }
    }

    // =====================================================================
    // SEARCH AND CONNECTIONS (the many-to-many relationships)
    //   Player <-> Team        through TeamMembership
    //   Team   <-> Tournament  through TournamentRegistration
    //   Player <-> Match       through PlayerMatchStat
    // =====================================================================

    // rows {id, name} for the drop-down lists (no header row)
    // kind is chosen by the program (TEAM, PLAYER, TOURNAMENT, MATCH, GAME), never typed by the user
    public ArrayList<String[]> pickList(String kind) throws SQLException {
        String sql;
        switch (kind) {
            case "TEAM":
                sql = "SELECT t.team_id, CONCAT(t.team_name, '  (', g.game_name, ')') FROM Team t "
                    + "JOIN Game g ON g.game_id = t.game_id ORDER BY t.team_name";
                break;
            case "PLAYER":
                sql = "SELECT player_id, CONCAT(gamer_tag, '  -  ', first_name, ' ', last_name) FROM Player "
                    + "ORDER BY gamer_tag";
                break;
            case "TOURNAMENT":
                sql = "SELECT tournament_id, CONCAT(tournament_name, '  (', status, ')') FROM Tournament "
                    + "ORDER BY start_date";
                break;
            case "MATCH":
                sql = "SELECT match_id, CONCAT(home_team, ' vs ', away_team, '  -  ', tournament_name, "
                    + "'  (', status, ')') FROM vw_MatchSchedule ORDER BY scheduled_at";
                break;
            case "GAME":
                sql = "SELECT game_id, game_name FROM Game ORDER BY game_id";
                break;
            case "COACH":
                sql = "SELECT c.coach_id, CONCAT(c.first_name, ' ', c.last_name, '  (', "
                    + "(SELECT COUNT(*) FROM Team t WHERE t.coach_id = c.coach_id), ' teams)') "
                    + "FROM Coach c ORDER BY c.first_name, c.last_name";
                break;
            default:
                throw new IllegalArgumentException("Unknown list: " + kind);
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);                                   // drop the header row
            return rows;
        }
    }

    // FIND TEAMS by name and game. Each row shows the team's current players (Team <-> Player)
    // and the tournaments it entered (Team <-> Tournament).
    public ArrayList<String[]> findTeams(String text, int gameId) throws SQLException {
        String sql = "SELECT t.team_id AS ID, t.team_name AS Team, g.game_name AS Game, "
                   + "CONCAT(c.first_name, ' ', c.last_name) AS Coach, t.region AS Region, "
                   + "(SELECT GROUP_CONCAT(p.gamer_tag ORDER BY p.gamer_tag SEPARATOR ', ') "
                   + "   FROM TeamMembership m JOIN Player p ON p.player_id = m.player_id "
                   + "  WHERE m.team_id = t.team_id AND m.leave_date IS NULL) AS `Current players`, "
                   + "(SELECT GROUP_CONCAT(tr.tournament_name ORDER BY tr.start_date SEPARATOR ', ') "
                   + "   FROM TournamentRegistration r JOIN Tournament tr ON tr.tournament_id = r.tournament_id "
                   + "  WHERE r.team_id = t.team_id) AS Tournaments "
                   + "FROM Team t JOIN Game g ON g.game_id = t.game_id JOIN Coach c ON c.coach_id = t.coach_id "
                   + "WHERE t.team_name LIKE ? AND (? = 0 OR t.game_id = ?) ORDER BY t.team_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + text + "%");
            ps.setInt(2, gameId);
            ps.setInt(3, gameId);
            return readTable(ps);
        }
    }

    // columns shared by the player searches: the player's current teams and every game they played
    private final String PLAYER_WITH_TEAMS =
            "SELECT p.player_id AS ID, p.gamer_tag AS `Gamer tag`, CONCAT(p.first_name, ' ', p.last_name) AS Name, "
          + "p.country AS Country, "
          + "(SELECT GROUP_CONCAT(t.team_name ORDER BY t.team_name SEPARATOR ', ') "
          + "   FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id "
          + "  WHERE m.player_id = p.player_id AND m.leave_date IS NULL) AS `Current teams`, "
          + "(SELECT GROUP_CONCAT(DISTINCT g.game_name ORDER BY g.game_name SEPARATOR ', ') "
          + "   FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id JOIN Game g ON g.game_id = t.game_id "
          + "  WHERE m.player_id = p.player_id) AS `Games played`, "
          + "(SELECT COUNT(*) FROM TeamMembership m WHERE m.player_id = p.player_id "
          + "  AND m.leave_date IS NOT NULL) AS `Past teams` "
          + "FROM Player p ";

    private final String NAME_MATCHES = "(p.gamer_tag LIKE ? OR p.first_name LIKE ? OR p.last_name LIKE ?) ";

    private final String IS_FREE_AGENT =
            "NOT EXISTS (SELECT 1 FROM TeamMembership f WHERE f.player_id = p.player_id AND f.leave_date IS NULL) ";

    // FIND PLAYERS by name, by game they have played (through their teams), or only free agents
    public ArrayList<String[]> findPlayers(String text, int gameId, boolean freeAgentsOnly) throws SQLException {
        String sql = PLAYER_WITH_TEAMS + "WHERE " + NAME_MATCHES
                   + "AND (? = 0 OR EXISTS (SELECT 1 FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id "
                   + "     WHERE m.player_id = p.player_id AND t.game_id = ?)) "
                   + (freeAgentsOnly ? "AND " + IS_FREE_AGENT : "")
                   + "ORDER BY p.gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + text + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setInt(4, gameId);
            ps.setInt(5, gameId);
            return readTable(ps);
        }
    }

    // COACH: players who are not on any of this coach's teams right now (free agents first)
    public ArrayList<String[]> availablePlayersForCoach(int coachId, String text, boolean freeAgentsOnly)
            throws SQLException {
        String sql = PLAYER_WITH_TEAMS + "WHERE " + NAME_MATCHES
                   + "AND NOT EXISTS (SELECT 1 FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id "
                   + "     WHERE m.player_id = p.player_id AND m.leave_date IS NULL AND t.coach_id = ?) "
                   + (freeAgentsOnly ? "AND " + IS_FREE_AGENT : "")
                   + "ORDER BY " + IS_FREE_AGENT + "DESC, p.gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + text + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setInt(4, coachId);
            return readTable(ps);
        }
    }

    // PLAYER: every team the player is NOT on, with my last join request, free places and its tournaments
    public ArrayList<String[]> otherTeamsForPlayer(int playerId) throws SQLException {
        String sql = "SELECT t.team_id AS ID, t.team_name AS Team, g.game_name AS Game, "
                   + "CONCAT(c.first_name, ' ', c.last_name) AS Coach, "
                   + "COALESCE((SELECT j.status FROM JoinRequest j WHERE j.team_id = t.team_id AND j.player_id = ? "
                   + "  ORDER BY j.requested_at DESC LIMIT 1), '-') AS `My request`, "
                   + "GREATEST(g.players_per_team + 2 - COUNT(m.player_id), 0) AS `Free places`, "
                   + "CASE WHEN EXISTS (SELECT 1 FROM TeamMembership mm JOIN Team tt ON tt.team_id = mm.team_id "
                   + "  WHERE mm.player_id = ? AND mm.leave_date IS NULL AND tt.game_id = t.game_id) "
                   + "  THEN 'Yes' ELSE 'No' END AS `Same game as my team`, "
                   + "COUNT(m.player_id) AS Players, t.region AS Region, "
                   + "(SELECT GROUP_CONCAT(tr.tournament_name ORDER BY tr.start_date SEPARATOR ', ') "
                   + "   FROM TournamentRegistration r JOIN Tournament tr ON tr.tournament_id = r.tournament_id "
                   + "  WHERE r.team_id = t.team_id) AS Tournaments "
                   + "FROM Team t JOIN Game g ON g.game_id = t.game_id JOIN Coach c ON c.coach_id = t.coach_id "
                   + "LEFT JOIN TeamMembership m ON m.team_id = t.team_id AND m.leave_date IS NULL "
                   + "WHERE t.team_id NOT IN (SELECT team_id FROM TeamMembership WHERE player_id = ? "
                   + "  AND leave_date IS NULL) "
                   + "GROUP BY t.team_id, t.team_name, g.game_name, c.first_name, c.last_name, t.region, "
                   + "g.players_per_team, t.game_id "
                   + "ORDER BY `Same game as my team` DESC, t.team_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setInt(2, playerId);
            ps.setInt(3, playerId);
            return readTable(ps);
        }
    }

    // PLAYER: everyone who is on a team with this player right now
    public ArrayList<String[]> teammates(int playerId) throws SQLException {
        String sql = "SELECT p.player_id AS `Player ID`, t.team_name AS Team, p.gamer_tag AS `Gamer tag`, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, m2.team_role AS Role, p.country AS Country, "
                   + "m2.join_date AS Joined "
                   + "FROM TeamMembership m1 "
                   + "JOIN TeamMembership m2 ON m2.team_id = m1.team_id AND m2.player_id <> m1.player_id "
                   + "     AND m2.leave_date IS NULL "
                   + "JOIN Player p ON p.player_id = m2.player_id JOIN Team t ON t.team_id = m1.team_id "
                   + "WHERE m1.player_id = ? AND m1.leave_date IS NULL "
                   + "ORDER BY t.team_name, FIELD(m2.team_role, 'CAPTAIN', 'STARTER', 'SUBSTITUTE'), p.gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    // PLAYER -> TEAMS -> TOURNAMENTS: tournaments the player's teams entered while the player was on them
    public ArrayList<String[]> playerTournaments(int playerId) throws SQLException {
        String sql = "SELECT tr.tournament_id AS `Tournament ID`, tr.tournament_name AS Tournament, "
                   + "t.team_name AS `With team`, tr.start_date AS Starts, "
                   + "tr.status AS Status, "
                   + "(SELECT COUNT(*) FROM PlayerMatchStat s JOIN TournamentMatch x ON x.match_id = s.match_id "
                   + "  WHERE s.player_id = m.player_id AND x.tournament_id = tr.tournament_id) AS `Matches I played` "
                   + "FROM TeamMembership m "
                   + "JOIN TournamentRegistration r ON r.team_id = m.team_id "
                   + "JOIN Tournament tr ON tr.tournament_id = r.tournament_id "
                   + "JOIN Team t ON t.team_id = m.team_id "
                   + "WHERE m.player_id = ? AND tr.end_date >= m.join_date "
                   + "  AND (m.leave_date IS NULL OR tr.start_date <= m.leave_date) "
                   + "ORDER BY tr.start_date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    // TEAM -> PLAYERS (current and past), with the other teams each player is on right now
    public ArrayList<String[]> teamPlayers(int teamId) throws SQLException {
        String sql = "SELECT p.player_id AS `Player ID`, p.gamer_tag AS `Gamer tag`, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, m.team_role AS Role, "
                   + "m.join_date AS Joined, COALESCE(m.leave_date, 'current') AS `Left`, "
                   + "(SELECT GROUP_CONCAT(t2.team_name SEPARATOR ', ') FROM TeamMembership m2 "
                   + "   JOIN Team t2 ON t2.team_id = m2.team_id "
                   + "  WHERE m2.player_id = p.player_id AND m2.team_id <> m.team_id AND m2.leave_date IS NULL) "
                   + "  AS `Also plays for` "
                   + "FROM TeamMembership m JOIN Player p ON p.player_id = m.player_id "
                   + "WHERE m.team_id = ? "
                   + "ORDER BY m.leave_date IS NULL DESC, FIELD(m.team_role, 'CAPTAIN', 'STARTER', 'SUBSTITUTE'), "
                   + "p.gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            return readTable(ps);
        }
    }

    // TEAM -> TOURNAMENTS, with the team's record in each tournament
    public ArrayList<String[]> teamTournaments(int teamId) throws SQLException {
        String sql = "SELECT s.tournament_id AS `Tournament ID`, s.tournament_name AS Tournament, "
                   + "tr.start_date AS Starts, tr.status AS Status, "
                   + "r.seed_number AS Seed, s.played AS Played, s.wins AS Won, s.losses AS Lost, s.points AS Points "
                   + "FROM vw_TournamentStandings s "
                   + "JOIN Tournament tr ON tr.tournament_id = s.tournament_id "
                   + "JOIN TournamentRegistration r ON r.tournament_id = s.tournament_id AND r.team_id = s.team_id "
                   + "WHERE s.team_id = ? ORDER BY tr.start_date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            return readTable(ps);
        }
    }

    // TOURNAMENT -> TEAMS, with coach, number of players and record
    public ArrayList<String[]> tournamentTeams(int tournamentId) throws SQLException {
        String sql = "SELECT t.team_id AS `Team ID`, r.seed_number AS Seed, t.team_name AS Team, "
                   + "CONCAT(c.first_name, ' ', c.last_name) AS Coach, "
                   + "(SELECT COUNT(*) FROM TeamMembership m WHERE m.team_id = t.team_id AND m.leave_date IS NULL) "
                   + "  AS Players, s.played AS Played, s.wins AS Won, s.losses AS Lost, s.points AS Points "
                   + "FROM TournamentRegistration r JOIN Team t ON t.team_id = r.team_id "
                   + "JOIN Coach c ON c.coach_id = t.coach_id "
                   + "JOIN vw_TournamentStandings s ON s.tournament_id = r.tournament_id AND s.team_id = r.team_id "
                   + "WHERE r.tournament_id = ? ORDER BY s.points DESC, r.seed_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            return readTable(ps);
        }
    }

    // TOURNAMENT -> TEAMS -> PLAYERS (two many-to-many relationships in one query)
    public ArrayList<String[]> tournamentPlayers(int tournamentId) throws SQLException {
        String sql = "SELECT p.player_id AS `Player ID`, t.team_name AS Team, p.gamer_tag AS `Gamer tag`, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, m.team_role AS Role, p.country AS Country, "
                   + "(SELECT COUNT(*) FROM PlayerMatchStat s JOIN TournamentMatch x ON x.match_id = s.match_id "
                   + "  WHERE s.player_id = p.player_id AND x.tournament_id = r.tournament_id) AS `Matches played` "
                   + "FROM TournamentRegistration r "
                   + "JOIN Team t ON t.team_id = r.team_id "
                   + "JOIN TeamMembership m ON m.team_id = t.team_id AND m.leave_date IS NULL "
                   + "JOIN Player p ON p.player_id = m.player_id "
                   + "WHERE r.tournament_id = ? "
                   + "ORDER BY t.team_name, FIELD(m.team_role, 'CAPTAIN', 'STARTER', 'SUBSTITUTE'), p.gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            return readTable(ps);
        }
    }

    // MATCH -> PLAYERS with their statistics in that match (Player <-> Match)
    public ArrayList<String[]> matchPlayers(int matchId) throws SQLException {
        String sql = "SELECT p.player_id AS `Player ID`, t.team_name AS Team, p.gamer_tag AS `Gamer tag`, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, s.kills AS Kills, s.deaths AS Deaths, "
                   + "s.assists AS Assists "
                   + "FROM PlayerMatchStat s "
                   + "JOIN TournamentMatch x ON x.match_id = s.match_id "
                   + "JOIN Player p ON p.player_id = s.player_id "
                   + "JOIN TeamMembership m ON m.player_id = p.player_id "
                   + "     AND m.team_id IN (x.home_team_id, x.away_team_id) "
                   + "JOIN Team t ON t.team_id = m.team_id "
                   + "WHERE s.match_id = ? ORDER BY t.team_name, s.kills DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            return readTable(ps);
        }
    }

    // =====================================================================
    // THE 5 REPORTS (each reads a view from esports_tms.sql; 0 = all)
    // =====================================================================

    // REPORT 1: tournament standings (3 points per win)
    public ArrayList<String[]> reportStandings(int tournamentId) throws SQLException {
        String sql = "SELECT team_id AS `Team ID`, tournament_name AS Tournament, team_name AS Team, played AS Played, "
                   + "wins AS Won, losses AS Lost, score_for AS Scored, score_against AS `Let in`, "
                   + "score_diff AS `Score difference`, points AS Points "
                   + "FROM vw_TournamentStandings WHERE (? = 0 OR tournament_id = ?) "
                   + "ORDER BY tournament_id, points DESC, score_diff DESC, team_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, tournamentId);
            return readTable(ps);
        }
    }

    // REPORT 2: match schedule and results
    public ArrayList<String[]> reportSchedule(int tournamentId) throws SQLException {
        String sql = "SELECT match_id AS ID, tournament_name AS Tournament, round_name AS Round, "
                   + "scheduled_at AS `Date/time`, home_team AS Home, away_team AS Away, "
                   + "CONCAT(COALESCE(home_score, '-'), ' : ', COALESCE(away_score, '-')) AS Score, "
                   + "COALESCE(winner, '') AS Winner, status AS Status "
                   + "FROM vw_MatchSchedule WHERE (? = 0 OR tournament_id = ?) ORDER BY scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, tournamentId);
            return readTable(ps);
        }
    }

    // REPORT 3: tournament registrations
    public ArrayList<String[]> reportRegistrations(int tournamentId) throws SQLException {
        String sql = "SELECT team_id AS `Team ID`, tournament_name AS Tournament, game_name AS Game, "
                   + "tournament_status AS Status, seed_number AS Seed, team_name AS Team, coach_name AS Coach, "
                   + "registration_date AS Registered "
                   + "FROM vw_TournamentRegistrations WHERE (? = 0 OR tournament_id = ?) "
                   + "ORDER BY tournament_id, seed_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, tournamentId);
            return readTable(ps);
        }
    }

    // REPORT 4: current team rosters
    public ArrayList<String[]> reportRosters(int teamId) throws SQLException {
        String sql = "SELECT team_name AS Team, game_name AS Game, coach_name AS Coach, player_id AS `Player ID`, "
                   + "gamer_tag AS `Gamer tag`, player_name AS Name, team_role AS Role, join_date AS Joined "
                   + "FROM vw_TeamRoster WHERE (? = 0 OR team_id = ?) "
                   + "ORDER BY team_id, FIELD(team_role, 'CAPTAIN', 'STARTER', 'SUBSTITUTE'), gamer_tag";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, teamId);
            return readTable(ps);
        }
    }

    // REPORT 5: player statistics, top N by kill/death ratio
    public ArrayList<String[]> reportPlayerStatistics(int topN) throws SQLException {
        String sql = "SELECT player_id AS `Player ID`, gamer_tag AS `Gamer tag`, player_name AS Name, "
                   + "matches_played AS Matches, total_kills AS Kills, total_deaths AS Deaths, "
                   + "total_assists AS Assists, kd_ratio AS `Kills per death` "
                   + "FROM vw_PlayerStatistics WHERE matches_played > 0 "
                   + "ORDER BY kd_ratio DESC, total_kills DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topN);
            return readTable(ps);
        }
    }

    // =====================================================================
    // HOME PAGE NUMBERS, CHART DATA, SEARCH, DETAILS AND UNDO
    // =====================================================================

    // first column of the first row as text ("0" if there is no row)
    private String one(String sql, int... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                String v = rs.next() ? rs.getString(1) : null;
                return v == null ? "0" : v;
            }
        }
    }

    // the 4 numbers on the home page, in a fixed order per role
    //   DIRECTOR: running tournaments, upcoming tournaments, matches waiting for a result, next of those,
    //             teams, games with teams, players, players without a team
    //   COACH:    my teams, my players, tournaments entered, upcoming ones, next match "date|home vs away",
    //             join requests waiting
    //   PLAYER:   my teams, matches played, kills per death, next match "date|home vs away"
    public String[] homeNumbers(String role, int profileId) throws SQLException {
        String myTeamIds = "SELECT team_id FROM Team WHERE coach_id = ?";
        String playerTeamIds = "SELECT team_id FROM TeamMembership WHERE player_id = ? AND leave_date IS NULL";
        String nextMatch = "SELECT CONCAT(scheduled_at, '|', home_team, ' vs ', away_team) FROM vw_MatchSchedule "
                         + "WHERE status = 'SCHEDULED' AND (home_team_id IN (%s) OR away_team_id IN (%s)) "
                         + "ORDER BY scheduled_at LIMIT 1";
        if (DIRECTOR.equals(role)) {
            return new String[] {
                one("SELECT COUNT(*) FROM Tournament WHERE status = 'ONGOING'"),
                one("SELECT COUNT(*) FROM Tournament WHERE status = 'UPCOMING'"),
                one("SELECT COUNT(*) FROM TournamentMatch WHERE status = 'SCHEDULED'"),
                one("SELECT MIN(scheduled_at) FROM TournamentMatch WHERE status = 'SCHEDULED'"),
                one("SELECT COUNT(*) FROM Team"),
                one("SELECT COUNT(DISTINCT game_id) FROM Team"),
                one("SELECT COUNT(*) FROM Player"),
                one("SELECT COUNT(*) FROM Player p WHERE NOT EXISTS (SELECT 1 FROM TeamMembership m "
                  + "WHERE m.player_id = p.player_id AND m.leave_date IS NULL)"),
            };
        } else if (COACH.equals(role)) {
            return new String[] {
                one("SELECT COUNT(*) FROM Team WHERE coach_id = ?", profileId),
                one("SELECT COUNT(DISTINCT player_id) FROM TeamMembership WHERE leave_date IS NULL AND team_id IN ("
                  + myTeamIds + ")", profileId),
                one("SELECT COUNT(DISTINCT tournament_id) FROM TournamentRegistration WHERE team_id IN ("
                  + myTeamIds + ")", profileId),
                one("SELECT COUNT(DISTINCT r.tournament_id) FROM TournamentRegistration r JOIN Tournament t "
                  + "ON t.tournament_id = r.tournament_id WHERE t.status = 'UPCOMING' AND r.team_id IN ("
                  + myTeamIds + ")", profileId),
                one(String.format(nextMatch, myTeamIds, myTeamIds), profileId, profileId),
                one("SELECT COUNT(*) FROM JoinRequest WHERE status = 'PENDING' AND team_id IN ("
                  + myTeamIds + ")", profileId),
            };
        } else {
            return new String[] {
                one("SELECT COUNT(*) FROM TeamMembership WHERE player_id = ? AND leave_date IS NULL", profileId),
                one("SELECT COUNT(*) FROM PlayerMatchStat WHERE player_id = ?", profileId),
                one("SELECT kd_ratio FROM vw_PlayerStatistics WHERE player_id = ?", profileId),
                one(String.format(nextMatch, playerTeamIds, playerTeamIds), profileId, profileId),
            };
        }
    }

    // rows {label, value, extra} for the home page chart
    //   DIRECTOR: points per team in the running (or last finished) tournament, extra = tournament name
    //   COACH:    kills per death of the best 5 players on my teams
    //   PLAYER:   my kills in my last 5 matches, extra = date
    public ArrayList<String[]> homeChart(String role, int profileId) throws SQLException {
        String sql;
        if (DIRECTOR.equals(role)) {
            sql = "SELECT team_name, points, tournament_name FROM vw_TournamentStandings WHERE tournament_id = "
                + "(SELECT tournament_id FROM Tournament WHERE status IN ('ONGOING', 'COMPLETED') "
                + " ORDER BY status = 'ONGOING' DESC, start_date DESC LIMIT 1) "
                + "ORDER BY points DESC, score_diff DESC, team_name";
        } else if (COACH.equals(role)) {
            sql = "SELECT gamer_tag, kd_ratio, '' FROM vw_PlayerStatistics WHERE matches_played > 0 AND player_id IN "
                + "(SELECT m.player_id FROM TeamMembership m JOIN Team t ON t.team_id = m.team_id "
                + " WHERE t.coach_id = ? AND m.leave_date IS NULL) ORDER BY kd_ratio DESC LIMIT 5";
        } else {
            sql = "SELECT * FROM (SELECT CONCAT(s.home_team, ' vs ', s.away_team), x.kills, s.scheduled_at "
                + "FROM PlayerMatchStat x JOIN vw_MatchSchedule s ON s.match_id = x.match_id "
                + "WHERE x.player_id = ? ORDER BY s.scheduled_at DESC LIMIT 5) last5 ORDER BY 3";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!DIRECTOR.equals(role)) {
                ps.setInt(1, profileId);
            }
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);
            return rows;
        }
    }

    // matches that still need to be played, for one coach's teams or for everyone (coachId 0)
    public ArrayList<String[]> upcomingMatches(int coachId) throws SQLException {
        String sql = "SELECT match_id AS `Match ID`, scheduled_at AS `Date/time`, home_team AS Home, "
                   + "away_team AS Away, round_name AS Round, tournament_name AS Tournament "
                   + "FROM vw_MatchSchedule WHERE status = 'SCHEDULED' AND (? = 0 OR home_team_id IN "
                   + "(SELECT team_id FROM Team WHERE coach_id = ?) OR away_team_id IN "
                   + "(SELECT team_id FROM Team WHERE coach_id = ?)) ORDER BY scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, coachId);
            ps.setInt(2, coachId);
            ps.setInt(3, coachId);
            return readTable(ps);
        }
    }

    // the last results, newest first
    public ArrayList<String[]> recentResults(int limit) throws SQLException {
        String sql = "SELECT match_id AS `Match ID`, scheduled_at AS `Date/time`, home_team AS Home, "
                   + "CONCAT(home_score, ' : ', away_score) AS Score, away_team AS Away, winner AS Winner, "
                   + "tournament_name AS Tournament "
                   + "FROM vw_MatchSchedule WHERE status = 'COMPLETED' ORDER BY scheduled_at DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readTable(ps);
        }
    }

    // all matches of one team
    public ArrayList<String[]> teamMatches(int teamId) throws SQLException {
        String sql = "SELECT match_id AS `Match ID`, scheduled_at AS `Date/time`, tournament_name AS Tournament, "
                   + "home_team AS Home, CONCAT(COALESCE(home_score, '-'), ' : ', COALESCE(away_score, '-')) AS Score, "
                   + "away_team AS Away, status AS Status "
                   + "FROM vw_MatchSchedule WHERE home_team_id = ? OR away_team_id = ? ORDER BY scheduled_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, teamId);
            return readTable(ps);
        }
    }

    // SEARCH EVERYTHING: teams, players and tournaments. LIKE finds parts of names,
    // SOUNDEX also finds names that sound the same (small typing mistakes).
    public ArrayList<String[]> searchEverything(String text) throws SQLException {
        String sql = "SELECT 'Team' AS Type, t.team_id AS ID, t.team_name AS Name, "
                   + "CONCAT(g.game_name, ' team from ', t.region) AS About "
                   + "FROM Team t JOIN Game g ON g.game_id = t.game_id "
                   + "WHERE t.team_name LIKE ? OR g.game_name LIKE ? OR SOUNDEX(t.team_name) = SOUNDEX(?) "
                   + "UNION ALL "
                   + "SELECT 'Player', p.player_id, p.gamer_tag, CONCAT(p.first_name, ' ', p.last_name, ', ', p.country) "
                   + "FROM Player p WHERE p.gamer_tag LIKE ? OR CONCAT(p.first_name, ' ', p.last_name) LIKE ? "
                   + "OR SOUNDEX(p.gamer_tag) = SOUNDEX(?) OR SOUNDEX(p.first_name) = SOUNDEX(?) "
                   + "OR SOUNDEX(p.last_name) = SOUNDEX(?) "
                   + "UNION ALL "
                   + "SELECT 'Tournament', tr.tournament_id, tr.tournament_name, CONCAT(g.game_name, ', ', tr.location) "
                   + "FROM Tournament tr JOIN Game g ON g.game_id = tr.game_id "
                   + "WHERE tr.tournament_name LIKE ? OR g.game_name LIKE ? OR tr.location LIKE ? "
                   + "ORDER BY Type DESC, Name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + text + "%";
            String[] values = {like, like, text, like, like, text, text, text, like, like, like};
            for (int i = 0; i < values.length; i++) {
                ps.setString(i + 1, values[i]);
            }
            return readTable(ps);
        }
    }

    // facts about one team / player / tournament / match for its details page (null if not found)
    //   TEAM:       name, game, region, coach, current players
    //   PLAYER:     gamer tag, full name, country, current teams
    //   TOURNAMENT: name, game, location, start, end, status
    //   MATCH:      home, away, round, tournament, date/time, status, home score, away score
    public String[] describe(String type, int id) throws SQLException {
        String sql;
        switch (type) {
            case "TEAM":
                sql = "SELECT t.team_name, g.game_name, t.region, CONCAT(c.first_name, ' ', c.last_name), "
                    + "(SELECT COUNT(*) FROM TeamMembership m WHERE m.team_id = t.team_id AND m.leave_date IS NULL) "
                    + "FROM Team t JOIN Game g ON g.game_id = t.game_id JOIN Coach c ON c.coach_id = t.coach_id "
                    + "WHERE t.team_id = ?";
                break;
            case "PLAYER":
                sql = "SELECT p.gamer_tag, CONCAT(p.first_name, ' ', p.last_name), p.country, "
                    + "(SELECT COUNT(*) FROM TeamMembership m WHERE m.player_id = p.player_id AND m.leave_date IS NULL) "
                    + "FROM Player p WHERE p.player_id = ?";
                break;
            case "TOURNAMENT":
                sql = "SELECT t.tournament_name, g.game_name, t.location, t.start_date, t.end_date, t.status "
                    + "FROM Tournament t JOIN Game g ON g.game_id = t.game_id WHERE t.tournament_id = ?";
                break;
            case "MATCH":
                sql = "SELECT home_team, away_team, round_name, tournament_name, scheduled_at, status, "
                    + "home_score, away_score FROM vw_MatchSchedule WHERE match_id = ?";
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ArrayList<String[]> rows = readTable(ps);
            return rows.size() > 1 ? rows.get(1) : null;
        }
    }

    // teams that may enter this tournament: same game, not registered yet
    public ArrayList<String[]> teamsForTournament(int tournamentId) throws SQLException {
        String sql = "SELECT t.team_id, t.team_name FROM Team t JOIN Tournament tr ON tr.game_id = t.game_id "
                   + "WHERE tr.tournament_id = ? AND t.team_id NOT IN "
                   + "(SELECT team_id FROM TournamentRegistration WHERE tournament_id = ?) ORDER BY t.team_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, tournamentId);
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);
            return rows;
        }
    }

    // upcoming tournaments this team may enter: same game, not registered yet
    public ArrayList<String[]> tournamentsForTeam(int teamId) throws SQLException {
        String sql = "SELECT tr.tournament_id, tr.tournament_name FROM Tournament tr JOIN Team t ON t.game_id = tr.game_id "
                   + "WHERE t.team_id = ? AND tr.status = 'UPCOMING' AND tr.tournament_id NOT IN "
                   + "(SELECT tournament_id FROM TournamentRegistration WHERE team_id = ?) ORDER BY tr.start_date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, teamId);
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);
            return rows;
        }
    }

    // UNDO helpers ---------------------------------------------------------

    // undo "register team": remove the registration again
    public boolean unregisterTeam(int tournamentId, int teamId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM TournamentRegistration WHERE tournament_id = ? AND team_id = ?")) {
            ps.setInt(1, tournamentId);
            ps.setInt(2, teamId);
            return ps.executeUpdate() == 1;
        }
    }

    // undo "record result": the match goes back to SCHEDULED without a score
    public boolean undoMatchResult(int matchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE TournamentMatch SET home_score = NULL, "
                + "away_score = NULL, status = 'SCHEDULED' WHERE match_id = ? AND status = 'COMPLETED'")) {
            ps.setInt(1, matchId);
            return ps.executeUpdate() == 1;
        }
    }

    // undo "schedule match": delete it again (only while it has no result)
    public boolean deleteScheduledMatch(int matchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM TournamentMatch WHERE match_id = ? AND status = 'SCHEDULED'")) {
            ps.setInt(1, matchId);
            return ps.executeUpdate() == 1;
        }
    }

    // the role a player has on a team right now (null if not on it), so "remove" can be undone
    public String currentRole(int teamId, int playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT team_role FROM TeamMembership "
                + "WHERE team_id = ? AND player_id = ? AND leave_date IS NULL")) {
            ps.setInt(1, teamId);
            ps.setInt(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // =====================================================================
    // JOIN REQUESTS: a player asks to join a team, the team's coach answers
    // =====================================================================

    // PLAYER: ask to join a team. Returns "SUCCESS: ..." or "NOT DONE: ..." like the procedures.
    public String askToJoin(int playerId, int teamId, String role, String message) throws SQLException {
        if (one("SELECT COUNT(*) FROM TeamMembership WHERE team_id = ? AND player_id = ? AND leave_date IS NULL",
                teamId, playerId).equals("1")) {
            return "NOT DONE: You are already on this team.";
        }
        if (!one("SELECT COUNT(*) FROM JoinRequest WHERE team_id = ? AND player_id = ? AND status = 'PENDING'",
                teamId, playerId).equals("0")) {
            return "NOT DONE: You already asked to join this team. Please wait for the coach to answer.";
        }
        String sql = "INSERT INTO JoinRequest (team_id, player_id, requested_role, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setInt(2, playerId);
            ps.setString(3, role);
            if (message == null || message.trim().isEmpty()) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, message.trim());
            }
            ps.executeUpdate();
        }
        return "SUCCESS: Request sent. The coach will see it on their Join requests page.";
    }

    // the id of my waiting request to this team (0 = none), so "Ask to join" can be undone
    public int pendingRequestId(int playerId, int teamId) throws SQLException {
        return Integer.parseInt(one("SELECT MAX(request_id) FROM JoinRequest WHERE player_id = ? AND team_id = ? "
                + "AND status = 'PENDING'", playerId, teamId));
    }

    // PLAYER: all my requests, newest first
    public ArrayList<String[]> myRequests(int playerId) throws SQLException {
        String sql = "SELECT j.request_id AS `Request ID`, t.team_id AS `Team ID`, t.team_name AS Team, "
                   + "g.game_name AS Game, CONCAT(c.first_name, ' ', c.last_name) AS Coach, "
                   + "j.requested_role AS `Role asked`, j.requested_at AS Sent, j.status AS Status, "
                   + "j.answered_at AS Answered "
                   + "FROM JoinRequest j JOIN Team t ON t.team_id = j.team_id JOIN Game g ON g.game_id = t.game_id "
                   + "JOIN Coach c ON c.coach_id = t.coach_id "
                   + "WHERE j.player_id = ? ORDER BY j.status = 'PENDING' DESC, j.requested_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return readTable(ps);
        }
    }

    // PLAYER: take back a request that is still waiting
    public boolean cancelRequest(int requestId, int playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE JoinRequest SET status = 'CANCELLED', "
                + "answered_at = NOW() WHERE request_id = ? AND player_id = ? AND status = 'PENDING'")) {
            ps.setInt(1, requestId);
            ps.setInt(2, playerId);
            return ps.executeUpdate() == 1;
        }
    }

    // COACH: requests for my teams (waitingOnly = only the ones I still have to answer)
    public ArrayList<String[]> requestsForCoach(int coachId, boolean waitingOnly) throws SQLException {
        String sql = "SELECT j.request_id AS `Request ID`, p.player_id AS `Player ID`, p.gamer_tag AS `Gamer tag`, "
                   + "t.team_name AS Team, j.requested_role AS `Role asked`, "
                   + "COALESCE(j.message, '-') AS Message, j.requested_at AS Sent, j.status AS Status, "
                   + "CONCAT(p.first_name, ' ', p.last_name) AS Name, p.country AS Country, "
                   + "(SELECT GROUP_CONCAT(t2.team_name SEPARATOR ', ') FROM TeamMembership m "
                   + "   JOIN Team t2 ON t2.team_id = m.team_id "
                   + "  WHERE m.player_id = p.player_id AND m.leave_date IS NULL) AS `Current teams` "
                   + "FROM JoinRequest j JOIN Team t ON t.team_id = j.team_id JOIN Player p ON p.player_id = j.player_id "
                   + "WHERE t.coach_id = ? " + (waitingOnly ? "AND j.status = 'PENDING' " : "")
                   + "ORDER BY j.status = 'PENDING' DESC, j.requested_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, coachId);
            return readTable(ps);
        }
    }

    // one request: {team_id, team name, player_id, gamer tag, requested role, status} or null
    public String[] requestInfo(int requestId) throws SQLException {
        String sql = "SELECT j.team_id, t.team_name, j.player_id, p.gamer_tag, j.requested_role, j.status "
                   + "FROM JoinRequest j JOIN Team t ON t.team_id = j.team_id JOIN Player p ON p.player_id = j.player_id "
                   + "WHERE j.request_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ArrayList<String[]> rows = readTable(ps);
            return rows.size() > 1 ? rows.get(1) : null;
        }
    }

    // STORED PROCEDURE 4 (CallableStatement): the coach approves; the player joins the team
    public String approveRequest(int requestId, int coachId, String role) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{CALL sp_ApproveJoinRequest(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, requestId);
            cs.setInt(2, coachId);
            cs.setString(3, role);
            cs.registerOutParameter(4, Types.TINYINT);
            cs.registerOutParameter(5, Types.VARCHAR);
            cs.execute();
            return procedureResult(cs, 4);
        }
    }

    // COACH: say no (only for requests to my own teams)
    public boolean declineRequest(int requestId, int coachId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE JoinRequest j JOIN Team t ON t.team_id = j.team_id "
                + "SET j.status = 'DECLINED', j.answered_at = NOW() "
                + "WHERE j.request_id = ? AND t.coach_id = ? AND j.status = 'PENDING'")) {
            ps.setInt(1, requestId);
            ps.setInt(2, coachId);
            return ps.executeUpdate() == 1;
        }
    }

    // undo for approve / decline / cancel: the request waits again
    public boolean reopenRequest(int requestId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE JoinRequest SET status = 'PENDING', answered_at = NULL WHERE request_id = ?")) {
            ps.setInt(1, requestId);
            return ps.executeUpdate() == 1;
        }
    }

    // a team's coach, for the team's details page:
    // {name, years of experience, phone, email, other teams of this coach}
    public String[] teamCoach(int teamId) throws SQLException {
        String sql = "SELECT CONCAT(c.first_name, ' ', c.last_name), c.years_experience, COALESCE(c.phone, '-'), "
                   + "u.email, COALESCE((SELECT GROUP_CONCAT(o.team_name ORDER BY o.team_name SEPARATOR ', ') "
                   + "   FROM Team o WHERE o.coach_id = c.coach_id AND o.team_id <> t.team_id), '-') "
                   + "FROM Team t JOIN Coach c ON c.coach_id = t.coach_id JOIN UserAccount u ON u.user_id = c.user_id "
                   + "WHERE t.team_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ArrayList<String[]> rows = readTable(ps);
            return rows.size() > 1 ? rows.get(1) : null;
        }
    }

    // =====================================================================
    // DROP OUT OF A TOURNAMENT
    // =====================================================================

    // STORED PROCEDURE 5 (CallableStatement): take a team out of a tournament
    public String withdrawTeam(int tournamentId, int teamId) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{CALL sp_WithdrawTeamFromTournament(?, ?, ?, ?)}")) {
            cs.setInt(1, tournamentId);
            cs.setInt(2, teamId);
            cs.registerOutParameter(3, Types.TINYINT);
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.execute();
            return procedureResult(cs, 3);
        }
    }

    // tournaments this team is in that are not over yet {id, name}
    public ArrayList<String[]> openTournamentsOfTeam(int teamId) throws SQLException {
        String sql = "SELECT tr.tournament_id, tr.tournament_name FROM TournamentRegistration r "
                   + "JOIN Tournament tr ON tr.tournament_id = r.tournament_id "
                   + "WHERE r.team_id = ? AND tr.status <> 'COMPLETED' ORDER BY tr.start_date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);
            return rows;
        }
    }

    // teams in this tournament {id, name}
    public ArrayList<String[]> teamsInTournament(int tournamentId) throws SQLException {
        String sql = "SELECT t.team_id, t.team_name FROM TournamentRegistration r JOIN Team t ON t.team_id = r.team_id "
                   + "WHERE r.tournament_id = ? ORDER BY t.team_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tournamentId);
            ArrayList<String[]> rows = readTable(ps);
            rows.remove(0);
            return rows;
        }
    }

    // how many not-played matches would be cancelled if this team leaves the tournament
    public int plannedMatches(int tournamentId, int teamId) throws SQLException {
        return Integer.parseInt(one("SELECT COUNT(*) FROM TournamentMatch WHERE tournament_id = ? "
                + "AND status = 'SCHEDULED' AND (home_team_id = ? OR away_team_id = ?)", tournamentId, teamId, teamId));
    }

    // =====================================================================
    // CREATE A TEAM (director: any coach; coach: themselves)
    // =====================================================================

    public boolean teamNameExists(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Team WHERE team_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // INSERT a team, returns its new id
    public int addTeam(String name, int gameId, int coachId, String region, String founded) throws SQLException {
        String sql = "INSERT INTO Team (team_name, game_id, coach_id, region, founded_date) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, gameId);
            ps.setInt(3, coachId);
            ps.setString(4, region);
            ps.setDate(5, java.sql.Date.valueOf(founded));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // undo "create team": DELETE, but only while nothing is connected to the team yet
    public boolean deleteNewTeam(int teamId) throws SQLException {
        String sql = "DELETE FROM Team WHERE team_id = ? "
                   + "AND NOT EXISTS (SELECT 1 FROM TeamMembership WHERE team_id = ?) "
                   + "AND NOT EXISTS (SELECT 1 FROM TournamentRegistration WHERE team_id = ?) "
                   + "AND NOT EXISTS (SELECT 1 FROM TournamentMatch WHERE home_team_id = ? OR away_team_id = ?) "
                   + "AND NOT EXISTS (SELECT 1 FROM JoinRequest WHERE team_id = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) {
                ps.setInt(i, teamId);
            }
            return ps.executeUpdate() == 1;
        }
    }

    // true when the database was made by the current esports_tms.sql (has the newest table and procedures)
    public boolean schemaIsCurrent() {
        try {
            return one("SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                     + "AND table_name = 'JoinRequest') + (SELECT COUNT(*) FROM information_schema.routines "
                     + "WHERE routine_schema = DATABASE() AND routine_name IN ('sp_ApproveJoinRequest', "
                     + "'sp_WithdrawTeamFromTournament'))").equals("3");
        } catch (SQLException e) {
            return false;
        }
    }

    // how much "delete tournament" also deletes: {teams registered, matches, statistics rows}
    public int[] tournamentDeleteSize(int tournamentId) throws SQLException {
        return new int[] {
            Integer.parseInt(one("SELECT COUNT(*) FROM TournamentRegistration WHERE tournament_id = ?", tournamentId)),
            Integer.parseInt(one("SELECT COUNT(*) FROM TournamentMatch WHERE tournament_id = ?", tournamentId)),
            Integer.parseInt(one("SELECT COUNT(*) FROM PlayerMatchStat s JOIN TournamentMatch m "
                    + "ON m.match_id = s.match_id WHERE m.tournament_id = ?", tournamentId)),
        };
    }
}
