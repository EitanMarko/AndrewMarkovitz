package microservices;

/**
 * PlayerData
 *
 * A simple Data Transfer Object (DTO) that carries all the information
 * needed to create a new player record in the database. The WorkerServer
 * deserializes the incoming HTTP/TCP request body into a PlayerData object
 * before passing it to PlayerHandler.createPlayer().
 *
 * This class is intentionally simple — no logic, just fields and accessors.
 * Validation is performed by PlayerHandler, not here.
 *
 * FIELDS:
 *   name      — The player's full name. Serves as the unique identifier
 *               (playerName == playerId) throughout the system.
 *   position  — The player's position (e.g. "RF", "SP", "C").
 *   teamName  — The name of the team this player belongs to. PlayerHandler
 *               will look up the team's ID in the database using this name
 *               and reject the player creation if the team doesn't exist.
 *
 * INITIAL STATS:
 *   All initial stat values default to 0 and are written into the player_stats
 *   table when the player record is created. The league can later use
 *   StatUpdater to update these values.
 */
public class PlayerData {

    // -------------------------------------------------------------------------
    // Required fields — PlayerHandler will reject creation if any of these
    // are null or empty.
    // -------------------------------------------------------------------------

    /** The player's full name. Used as their unique identifier in the system. */
    private String name;

    /**
     * The player's fielding/batting position.
     * E.g.: "RF" (right field), "SP" (starting pitcher), "C" (catcher), "DH" (designated hitter)
     */
    private String position;

    /**
     * The name of the team this player plays for.
     * Must exactly match a team name that already exists in the teams table.
     * If it doesn't, PlayerHandler will return an error instead of creating the player.
     */
    private String teamName;

    // -------------------------------------------------------------------------
    // Initial stat values — all default to 0.
    // The league may populate these for bulk-loading historical data.
    // -------------------------------------------------------------------------

    /** Initial home run count (typically 0 for a new season). */
    private int homeRuns      = 0;

    /** Initial hit count. */
    private int hits          = 0;

    /** Initial at-bat count. */
    private int atBats        = 0;

    /**
     * Initial batting average.
     * For a brand-new player with no at-bats, this is 0.000.
     * For historical data loads, this can be pre-computed.
     */
    private double battingAverage = 0.000;

    /** Initial strikeout count. */
    private int strikeouts    = 0;

    /** Initial walk count. */
    private int walks         = 0;

    /** Initial stolen base count. */
    private int stolenBases   = 0;

    /** Initial runs batted in count. */
    private int runsBattedIn  = 0;

    /**
     * The league this player belongs to (e.g. "MLB", "NBA").
     * Set by LeagueInterfaceImpl from the leagueId passed to its constructor.
     * Stored in players.league_id so fan queries can route to the correct stats table.
     */
    private String leagueId = "MLB";

    /**
     * The year for which this initial stat row should be created.
     * Defaults to the current calendar year, but can be overridden
     * when loading historical data from prior seasons.
     */
    private int year = java.time.Year.now().getValue();

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-arg constructor for deserialization frameworks (e.g. Jackson, Gson). */
    public PlayerData() {}

    /**
     * Convenience constructor for the common case: creating a brand-new player
     * with all stats at zero.
     *
     * @param name      The player's full name
     * @param position  The player's position
     * @param teamName  The name of the player's team (must exist in DB)
     */
    public PlayerData(String name, String position, String teamName) {
        this.name     = name;
        this.position = position;
        this.teamName = teamName;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getName()          { return name; }
    public void   setName(String n)  { this.name = n; }

    public String getPosition()           { return position; }
    public void   setPosition(String p)   { this.position = p; }

    public String getTeamName()           { return teamName; }
    public void   setTeamName(String t)   { this.teamName = t; }

    public int    getHomeRuns()           { return homeRuns; }
    public void   setHomeRuns(int v)      { this.homeRuns = v; }

    public int    getHits()               { return hits; }
    public void   setHits(int v)          { this.hits = v; }

    public int    getAtBats()             { return atBats; }
    public void   setAtBats(int v)        { this.atBats = v; }

    public double getBattingAverage()          { return battingAverage; }
    public void   setBattingAverage(double v)  { this.battingAverage = v; }

    public int    getStrikeouts()         { return strikeouts; }
    public void   setStrikeouts(int v)    { this.strikeouts = v; }

    public int    getWalks()              { return walks; }
    public void   setWalks(int v)         { this.walks = v; }

    public int    getStolenBases()        { return stolenBases; }
    public void   setStolenBases(int v)   { this.stolenBases = v; }

    public int    getRunsBattedIn()         { return runsBattedIn; }
    public void   setRunsBattedIn(int v)   { this.runsBattedIn = v; }

    public String getLeagueId()            { return leagueId; }
    public void   setLeagueId(String l)    { this.leagueId = l; }

    public int    getYear()                { return year; }
    public void   setYear(int y)          { this.year = y; }

    @Override
    public String toString() {
        return String.format("PlayerData{name='%s', position='%s', team='%s', year=%d}",
                name, position, teamName, year);
    }
}
