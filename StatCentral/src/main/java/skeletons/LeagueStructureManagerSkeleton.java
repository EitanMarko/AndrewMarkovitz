package skeletons;
/**
 * LeagueStructureManager
 *
 * A microservice object held by a WorkerServer. Responsible for managing and
 * modifying the hierarchical league structure (conferences, divisions, teams)
 * stored in the underlying database, as well as moving players, teams, and
 * divisions within that structure.
 */
public class LeagueStructureManagerSkeleton {

    /**
     * Creates a new conference in the underlying database with the given name.
     *
     * @param conferenceName    The name of the new conference to be created
     * @return                  A success message confirming the conference was created,
     *                          or an error message if a conference with that name already exists
     */
    public String createConference(String conferenceName) {return null;}

    /**
     * Creates a new division within an existing conference in the underlying database.
     *
     * @param divisionName      The name of the new division to be created
     * @param conferenceName    The name of the existing conference under which the
     *                          new division will be placed
     * @return                  A success message confirming the division was created,
     *                          or an error message if the specified conference does not exist
     *                          or a division with that name already exists within it
     */
    public String createDivision(String divisionName, String conferenceName) {return null;}

    /**
     * Creates a new team within an existing division in the underlying database.
     *
     * @param teamName          The name of the new team to be created
     * @param divisionName      The name of the existing division under which the
     *                          new team will be placed
     * @return                  A success message confirming the team was created,
     *                          or an error message if the specified division does not exist
     *                          or a team with that name already exists within it
     */
    public String createTeam(String teamName, String divisionName) {return null;}

    /**
     * Moves a player from their current team to a different existing team
     * in the underlying database.
     *
     * @param playerId      The unique identifier of the player to be moved
     * @param newTeam       The name of the team to move the player to
     * @return              A success message confirming the player was moved,
     *                      or an error message if the player or target team does not exist
     */
    public String movePlayerToTeam(String playerId, String newTeam) {return null;}

    /**
     * Moves a team from its current division to a different existing division
     * in the underlying database.
     *
     * @param teamName        The name of the team to be moved
     * @param newDivision     The name of the division to move the team to
     * @return                A success message confirming the team was moved,
     *                        or an error message if the team or target division does not exist
     */
    public String moveTeamToDivision(String teamName, String newDivision) {return null;}

    /**
     * Moves a division from its current conference to a different existing conference
     * in the underlying database.
     *
     * @param divisionName      The name of the division to be moved
     * @param newConference     The name of the conference to move the division to
     * @return                  A success message confirming the division was moved,
     *                          or an error message if the division or target conference
     *                          does not exist
     */
    public String moveDivisionToConference(String divisionName, String newConference) {return null;}

    /**
     * Deletes an existing conference from the underlying database. Also permanently
     * deletes all divisions within that conference, all teams within those divisions,
     * and all player profiles belonging to those teams.
     *
     * @param conferenceName  The name of the conference to be deleted
     * @return                A success message confirming the conference and all of its
     *                        contents were deleted, or an error message if the conference
     *                        does not exist
     */
    public String deleteConference(String conferenceName) {return null;}

    /**
     * Deletes an existing division from the underlying database. Also permanently
     * deletes all teams within that division and all player profiles belonging
     * to those teams.
     *
     * @param divisionName  The name of the division to be deleted
     * @return              A success message confirming the division and all of its
     *                      contents were deleted, or an error message if the division
     *                      does not exist
     */
    public String deleteDivision(String divisionName) {return null;}

    /**
     * Deletes an existing team from the underlying database. Also permanently
     * deletes all player profiles belonging to that team.
     *
     * @param teamName  The name of the team to be deleted
     * @return          A success message confirming the team and all of its players
     *                  were deleted, or an error message if the team does not exist
     */
    public String deleteTeam(String teamName) {return null;}
}
