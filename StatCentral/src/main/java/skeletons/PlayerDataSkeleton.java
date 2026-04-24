package skeletons;
/**
 * PlayerData
 *
 * A data transfer object used to carry all fields associated with a player profile.
 * Passed into PlayerCreator when creating a new player in the underlying database.
 * Contains identifying fields (name, team) as well as all statistical fields
 * tracked by the StatUpdater microservice.
 *
 * Note: "doubles" is used as the field name for two-base hits, as "double"
 * is a reserved keyword in Java.
 */
public class PlayerDataSkeleton {

    private String name;
    private String team;

    // Statistical fields (mirroring all stats tracked by StatUpdater)
    private int atBat;
    private int plateAppearance;
    private int hit;
    private int doubles;       // Named "doubles" to avoid collision with Java reserved keyword "double"
    private int triples;
    private int homeRun;
    private int rbi;
    private int stolenBase;
    private int run;
    private int walk;
    private int hitByPitch;

    // -------------------------------------------------------------------------
    // Getters and Setters — Identifying Fields
    // -------------------------------------------------------------------------

    /**
     * Returns the player's name.
     *
     * @return    The name of the player
     */
    public String getName() {return null;}

    /**
     * Sets the player's name.
     *
     * @param name    The name to assign to the player
     */
    public void setName(String name) {}

    /**
     * Returns the name of the team this player belongs to.
     *
     * @return    The team name of the player
     */
    public String getTeam() {return null;}

    /**
     * Sets the name of the team this player belongs to.
     * The team must exist in the defined league structure at the time
     * the player profile is created.
     *
     * @param team    The team name to assign to the player
     */
    public void setTeam(String team) {}

    // -------------------------------------------------------------------------
    // Getters and Setters — Statistical Fields
    // -------------------------------------------------------------------------

    /**
     * Returns the player's at-bat count.
     *
     * @return    The at-bat count
     */
    public int getAtBat() {return 0;}

    /**
     * Sets the player's at-bat count.
     *
     * @param atBat    The at-bat count to assign
     */
    public void setAtBat(int atBat) {}

    /**
     * Returns the player's plate appearance count.
     *
     * @return    The plate appearance count
     */
    public int getPlateAppearance() {return 0;}

    /**
     * Sets the player's plate appearance count.
     *
     * @param plateAppearance    The plate appearance count to assign
     */
    public void setPlateAppearance(int plateAppearance) {}

    /**
     * Returns the player's hit count.
     *
     * @return    The hit count
     */
    public int getHit() {return 0;}

    /**
     * Sets the player's hit count.
     *
     * @param hit    The hit count to assign
     */
    public void setHit(int hit) {}

    /**
     * Returns the player's doubles count.
     *
     * @return    The doubles count
     */
    public int getDoubles() {return 0;}

    /**
     * Sets the player's doubles count.
     *
     * @param doubles    The doubles count to assign
     */
    public void setDoubles(int doubles) {}

    /**
     * Returns the player's triples count.
     *
     * @return    The triples count
     */
    public int getTriples() {return 0;}

    /**
     * Sets the player's triples count.
     *
     * @param triples    The triples count to assign
     */
    public void setTriples(int triples) {}

    /**
     * Returns the player's home run count.
     *
     * @return    The home run count
     */
    public int getHomeRun() {return 0;}

    /**
     * Sets the player's home run count.
     *
     * @param homeRun    The home run count to assign
     */
    public void setHomeRun(int homeRun) {}

    /**
     * Returns the player's RBI (runs batted in) count.
     *
     * @return    The RBI count
     */
    public int getRbi() {return 0;}

    /**
     * Sets the player's RBI count.
     *
     * @param rbi    The RBI count to assign
     */
    public void setRbi(int rbi) {}

    /**
     * Returns the player's stolen base count.
     *
     * @return    The stolen base count
     */
    public int getStolenBase() {return 0;}

    /**
     * Sets the player's stolen base count.
     *
     * @param stolenBase    The stolen base count to assign
     */
    public void setStolenBase(int stolenBase) {}

    /**
     * Returns the player's run count.
     *
     * @return    The run count
     */
    public int getRun() {return 0;}

    /**
     * Sets the player's run count.
     *
     * @param run    The run count to assign
     */
    public void setRun(int run) {}

    /**
     * Returns the player's walk count.
     *
     * @return    The walk count
     */
    public int getWalk() {return 0;}

    /**
     * Sets the player's walk count.
     *
     * @param walk    The walk count to assign
     */
    public void setWalk(int walk) {}

    /**
     * Returns the player's hit-by-pitch count.
     *
     * @return    The hit-by-pitch count
     */
    public int getHitByPitch() {return 0;}

    /**
     * Sets the player's hit-by-pitch count.
     *
     * @param hitByPitch    The hit-by-pitch count to assign
     */
    public void setHitByPitch(int hitByPitch) {}
}
