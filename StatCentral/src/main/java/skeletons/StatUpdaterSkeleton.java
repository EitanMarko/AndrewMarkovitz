package skeletons;

import java.util.Set;

/**
 * StatUpdater
 *
 * Abstract base class for all sport-specific stat updater microservice objects.
 * A StatUpdater is held by a WorkerServer and is responsible for updating player
 * statistics in the underlying database.
 *
 * StatCentral is designed to support any sports league. Each sport that uses the
 * platform provides its own concrete StatUpdater subclass (e.g. MLBStatUpdater,
 * NFLStatUpdater, NBAStatUpdater) that defines the statistical categories tracked
 * for that sport and implements the corresponding database update logic.
 *
 * ── Design: Template Method Pattern ─────────────────────────────────────────
 *
 * This class applies the Template Method pattern. The two public-facing methods
 * that the WorkerServer calls — incrementStat() and setStat() — are declared
 * final here. They perform all shared validation logic (stat name recognition,
 * value constraint checking) before delegating to the protected abstract methods
 * doIncrementStat() and doSetStat(), which each concrete subclass implements with
 * sport-specific database logic.
 *
 * This guarantees that:
 *   - Validation is enforced uniformly across all sports. No subclass can bypass it.
 *   - Subclasses are only responsible for the sport-specific parts: declaring which
 *     stats exist and how to update each one in the database.
 *   - The WorkerServer's interaction contract never changes, regardless of which
 *     sport's StatUpdater is installed.
 *
 * ── How to extend for a new sport ───────────────────────────────────────────
 *
 * To add StatUpdater support for a new sport, create a subclass and:
 *
 *   1. Implement getSupportedStats() to return the Set of all stat category names
 *      that are valid for this sport (e.g. "touchdowns", "rushingYards" for NFL).
 *      These names are the canonical identifiers used in requests from the
 *      LeagueInterface — they must match exactly what callers send.
 *
 *   2. Implement doIncrementStat(playerId, statName) to handle an increment-by-one
 *      update for any stat name in the set returned by getSupportedStats().
 *      The statName is guaranteed to be a member of that set when this method is
 *      called; no redundant validation is needed.
 *
 *   3. Implement doSetStat(playerId, statName, value) to handle a direct-value-set
 *      update for any stat name in the set returned by getSupportedStats().
 *      The statName is guaranteed to be a member of that set and value is
 *      guaranteed to be non-negative when this method is called.
 *
 * Example skeleton for a new sport:
 *
 *   public class NFLStatUpdater extends StatUpdater {
 *
 *       private static final Set<String> SUPPORTED_STATS = Set.of(
 *           "touchdowns", "rushingYards", "receivingYards", "passingYards",
 *           "interceptions", "sacks", "fieldGoals"
 *       );
 *
 *       {@literal @}Override
 *       public Set<String> getSupportedStats() {
 *           return SUPPORTED_STATS;
 *       }
 *
 *       {@literal @}Override
 *       protected String doIncrementStat(String playerId, String statName) {
 *           // issue the appropriate UPDATE for each statName
 *       }
 *
 *       {@literal @}Override
 *       protected String doSetStat(String playerId, String statName, int value) {
 *           // issue the appropriate UPDATE for each statName
 *       }
 *   }
 */
public abstract class StatUpdaterSkeleton {

    // -------------------------------------------------------------------------
    // Public API — called by WorkerServer
    // These methods are final: validation logic must not be bypassed or overridden
    // by any subclass.
    // -------------------------------------------------------------------------

    /**
     * Increments the specified statistical category by one for the given player.
     * This is the method the WorkerServer calls to record a new in-game occurrence
     * (e.g. a batter getting a hit, a basketball player making a free throw).
     *
     * Validates that the stat name is recognized for this sport before delegating
     * to the sport-specific implementation in doIncrementStat().
     *
     * @param playerId    The unique identifier of the player to update
     * @param statName    The name of the statistical category to increment
     *                    (e.g. "homeRun", "touchdowns"). Must be a member of the
     *                    set returned by getSupportedStats() for this sport.
     * @return            A success message confirming the stat was incremented,
     *                    or an error message if the player does not exist or the
     *                    stat category is not defined for this sport
     */
    public final String incrementStat(String playerId, String statName) {
        if (!getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName + "' is not defined for this sport.";
        }
        return doIncrementStat(playerId, statName);
    }

    /**
     * Sets the specified statistical category to the given value for the given player.
     * This is the method the WorkerServer calls to directly assign a stat value
     * (e.g. correcting a data entry error, bulk-loading historical statistics).
     *
     * Validates that the stat name is recognized for this sport and that the value
     * satisfies the non-negativity constraint before delegating to the sport-specific
     * implementation in doSetStat().
     *
     * @param playerId    The unique identifier of the player to update
     * @param statName    The name of the statistical category to set
     *                    (e.g. "homeRun", "touchdowns"). Must be a member of the
     *                    set returned by getSupportedStats() for this sport.
     * @param value       The value to assign to the stat. Must be non-negative.
     * @return            A success message confirming the stat was set,
     *                    or an error message if the player does not exist, the
     *                    stat category is not defined for this sport, or the
     *                    value is negative
     */
    public final String setStat(String playerId, String statName, int value) {
        if (!getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName + "' is not defined for this sport.";
        }
        if (value < 0) {
            return "Error: stat value cannot be negative (received " + value
                    + " for stat '" + statName + "').";
        }
        return doSetStat(playerId, statName, value);
    }

    /**
     * Returns whether the specified stat category name is defined for this sport.
     * Convenience method for callers who want to check support before calling
     * incrementStat() or setStat().
     *
     * @param statName    The name of the statistical category to check
     * @return            true if this sport's StatUpdater recognizes the stat name,
     *                    false otherwise
     */
    public boolean supportsStat(String statName) {
        return getSupportedStats().contains(statName);
    }

    // -------------------------------------------------------------------------
    // Abstract methods — must be implemented by each sport-specific subclass
    // -------------------------------------------------------------------------

    /**
     * Returns the complete set of statistical category names that are defined and
     * tracked for this sport. This set serves as the canonical registry of valid
     * stat names. The strings in this set must exactly match the stat name strings
     * that callers pass into incrementStat() and setStat() via the LeagueInterface.
     *
     * This method should return a constant set (defined as a static final field in
     * the subclass) and must never return null.
     *
     * @return    An unmodifiable Set of all valid stat category name strings for
     *            this sport
     */
    public abstract Set<String> getSupportedStats();

    /**
     * Performs the sport-specific increment-by-one database update for the given
     * player and stat category. This method is called by incrementStat() after all
     * shared validation has already passed — implementors do not need to re-validate
     * the stat name or the player ID format.
     *
     * Implementations are responsible for issuing the appropriate SQL UPDATE to the
     * database and returning a result string.
     *
     * @param playerId    The unique identifier of the player to update.
     *                    Guaranteed to be non-null.
     * @param statName    The name of the statistical category to increment.
     *                    Guaranteed to be a member of getSupportedStats().
     * @return            A success message confirming the stat was incremented,
     *                    or an error message if the player does not exist in the
     *                    database or the database operation fails
     */
    protected abstract String doIncrementStat(String playerId, String statName);

    /**
     * Performs the sport-specific direct-value-set database update for the given
     * player and stat category. This method is called by setStat() after all
     * shared validation has already passed — implementors do not need to re-validate
     * the stat name, the value's non-negativity, or the player ID format.
     *
     * Implementations are responsible for issuing the appropriate SQL UPDATE to the
     * database and returning a result string.
     *
     * @param playerId    The unique identifier of the player to update.
     *                    Guaranteed to be non-null.
     * @param statName    The name of the statistical category to set.
     *                    Guaranteed to be a member of getSupportedStats().
     * @param value       The value to assign to the stat.
     *                    Guaranteed to be non-negative.
     * @return            A success message confirming the stat was set,
     *                    or an error message if the player does not exist in the
     *                    database or the database operation fails
     */
    protected abstract String doSetStat(String playerId, String statName, int value);
}