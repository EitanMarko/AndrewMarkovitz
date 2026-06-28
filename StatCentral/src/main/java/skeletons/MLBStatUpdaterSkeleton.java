package skeletons;

import java.util.Set;

/**
 * MLBStatUpdater
 *
 * Concrete StatUpdater implementation for MLB (Major League Baseball).
 * Extends the abstract StatUpdater base class, registering the statistical
 * categories tracked for MLB and implementing the sport-specific database
 * update logic for each.
 *
 * This class fulfills two responsibilities:
 *
 *   1. It implements the three abstract contract methods required by StatUpdater:
 *      getSupportedStats(), doIncrementStat(), and doSetStat(). These methods
 *      are what the base class's template methods (incrementStat / setStat) call
 *      after completing shared validation. The WorkerServer interacts with this
 *      class exclusively through the public API on the base class.
 *
 *   2. It retains named, strongly-typed public methods for each individual stat
 *      (e.g. incrementHomeRun(), setAtBat()). These are kept because they provide
 *      a clear, self-documenting MLB-specific API and can be called directly in
 *      contexts where the sport is known at compile time (e.g. unit tests,
 *      sport-specific tooling). doIncrementStat() and doSetStat() delegate to
 *      these named methods.
 *
 * ── How this fits into the system ───────────────────────────────────────────
 *
 * The WorkerServer holds a reference of type StatUpdater (the abstract base
 * class). When configured for MLB, a MLBStatUpdater instance is assigned to
 * that field. The WorkerServer calls statUpdater.incrementStat(playerId, statName)
 * or statUpdater.setStat(playerId, statName, value). The base class validates
 * the inputs, then calls doIncrementStat() or doSetStat() on this class, which
 * dispatches to the appropriate named method and issues the database UPDATE.
 *
 * To configure a WorkerServer for a different sport, replace the MLBStatUpdater
 * instance with the appropriate subclass (e.g. NFLStatUpdater). No other change
 * to WorkerServer or any upstream component is needed.
 */
public class MLBStatUpdaterSkeleton extends StatUpdaterSkeleton {

    /**
     * The complete set of statistical category names tracked for MLB.
     * These strings are the canonical identifiers used in requests from the
     * LeagueInterface and must match exactly what callers pass as statName.
     */
    private static final Set<String> SUPPORTED_STATS = Set.of(
            "atBat",
            "plateAppearance",
            "hit",
            "doubles",
            "triples",
            "homeRun",
            "rbi",
            "stolenBase",
            "run",
            "walk",
            "hitByPitch"
    );

    // -------------------------------------------------------------------------
    // Implementation of abstract contract methods required by StatUpdater
    // -------------------------------------------------------------------------

    /**
     * Returns the set of all statistical category names defined for MLB.
     * The base class uses this set to validate stat names before dispatching
     * to doIncrementStat() or doSetStat().
     *
     * @return    An unmodifiable Set of all valid MLB stat category name strings
     */
    @Override
    public Set<String> getSupportedStats() {
        return SUPPORTED_STATS;
    }

    /**
     * Dispatches an increment-by-one update to the appropriate named MLB stat
     * method based on the provided stat name. Called by the base class after
     * validation has confirmed that statName is a member of SUPPORTED_STATS.
     *
     * @param playerId    The unique identifier of the player to update
     * @param statName    A validated MLB stat category name
     * @return            The result string from the delegated named method
     */
    @Override
    protected String doIncrementStat(String playerId, String statName) {
        switch (statName) {
            case "atBat":           return incrementAtBat(playerId);
            case "plateAppearance": return incrementPlateAppearance(playerId);
            case "hit":             return incrementHit(playerId);
            case "doubles":         return incrementDouble(playerId);
            case "triples":         return incrementTriple(playerId);
            case "homeRun":         return incrementHomeRun(playerId);
            case "rbi":             return incrementRBI(playerId);
            case "stolenBase":      return incrementStolenBase(playerId);
            case "run":             return incrementRun(playerId);
            case "walk":            return incrementWalk(playerId);
            case "hitByPitch":      return incrementHitByPitch(playerId);
            default:
                // Unreachable: base class guarantees statName is in SUPPORTED_STATS
                return "Error: unexpected stat name '" + statName + "' reached doIncrementStat.";
        }
    }

    /**
     * Dispatches a direct-value-set update to the appropriate named MLB stat
     * method based on the provided stat name. Called by the base class after
     * validation has confirmed that statName is a member of SUPPORTED_STATS
     * and that value is non-negative.
     *
     * @param playerId    The unique identifier of the player to update
     * @param statName    A validated MLB stat category name
     * @param value       A validated non-negative value to assign to the stat
     * @return            The result string from the delegated named method
     */
    @Override
    protected String doSetStat(String playerId, String statName, int value) {
        switch (statName) {
            case "atBat":           return setAtBat(playerId, value);
            case "plateAppearance": return setPlateAppearance(playerId, value);
            case "hit":             return setHit(playerId, value);
            case "doubles":         return setDouble(playerId, value);
            case "triples":         return setTriple(playerId, value);
            case "homeRun":         return setHomeRun(playerId, value);
            case "rbi":             return setRBI(playerId, value);
            case "stolenBase":      return setStolenBase(playerId, value);
            case "run":             return setRun(playerId, value);
            case "walk":            return setWalk(playerId, value);
            case "hitByPitch":      return setHitByPitch(playerId, value);
            default:
                // Unreachable: base class guarantees statName is in SUPPORTED_STATS
                return "Error: unexpected stat name '" + statName + "' reached doSetStat.";
        }
    }

    // -------------------------------------------------------------------------
    // Named MLB increment methods
    // Each issues a SQL UPDATE incrementing the corresponding column by 1.
    // Called internally by doIncrementStat(); may also be called directly.
    // -------------------------------------------------------------------------

    /**
     * Increments a given player's at-bat count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementAtBat(String playerId) { return null; }

    /**
     * Increments a given player's plate appearance count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementPlateAppearance(String playerId) { return null; }

    /**
     * Increments a given player's hit count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementHit(String playerId) { return null; }

    /**
     * Increments a given player's doubles count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementDouble(String playerId) { return null; }

    /**
     * Increments a given player's triples count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementTriple(String playerId) { return null; }

    /**
     * Increments a given player's home run count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementHomeRun(String playerId) { return null; }

    /**
     * Increments a given player's RBI count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementRBI(String playerId) { return null; }

    /**
     * Increments a given player's stolen base count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementStolenBase(String playerId) { return null; }

    /**
     * Increments a given player's run count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementRun(String playerId) { return null; }

    /**
     * Increments a given player's walk count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementWalk(String playerId) { return null; }

    /**
     * Increments a given player's hit-by-pitch count by one in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String incrementHitByPitch(String playerId) { return null; }

    // -------------------------------------------------------------------------
    // Named MLB setter methods
    // Each issues a SQL UPDATE setting the corresponding column to the given value.
    // Called internally by doSetStat(); may also be called directly.
    // -------------------------------------------------------------------------

    /**
     * Sets a given player's at-bat count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the at-bat count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setAtBat(String playerId, int value) { return null; }

    /**
     * Sets a given player's plate appearance count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the plate appearance count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setPlateAppearance(String playerId, int value) { return null; }

    /**
     * Sets a given player's hit count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the hit count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setHit(String playerId, int value) { return null; }

    /**
     * Sets a given player's doubles count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the doubles count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setDouble(String playerId, int value) { return null; }

    /**
     * Sets a given player's triples count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the triples count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setTriple(String playerId, int value) { return null; }

    /**
     * Sets a given player's home run count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the home run count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setHomeRun(String playerId, int value) { return null; }

    /**
     * Sets a given player's RBI count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the RBI count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setRBI(String playerId, int value) { return null; }

    /**
     * Sets a given player's stolen base count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the stolen base count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setStolenBase(String playerId, int value) { return null; }

    /**
     * Sets a given player's run count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the run count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setRun(String playerId, int value) { return null; }

    /**
     * Sets a given player's walk count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the walk count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setWalk(String playerId, int value) { return null; }

    /**
     * Sets a given player's hit-by-pitch count to the specified value in the underlying database.
     *
     * @param playerId    The unique identifier of the player to update
     * @param value       The value to set the hit-by-pitch count to
     * @return            A success message confirming the update, or an error message
     *                    if the player does not exist
     */
    public String setHitByPitch(String playerId, int value) { return null; }
}
