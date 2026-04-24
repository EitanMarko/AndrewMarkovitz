package cluster;

import microservices.PlayerData;
import user.FanInterfaceImpl;
import user.LeagueInterfaceImpl;

/**
 * RunStatDemo
 *
 * A standalone client that exercises the StatCentral API end-to-end against a
 * running cluster.  Connects to the GatewayServer's HTTP port and performs:
 *
 *   Phase 1 — populate()
 *     Builds a real AL East structure (American League → AL East → Yankees,
 *     Red Sox, Orioles), registers 10 real MLB players across all three teams,
 *     updates each player's stats, and runs fan-facing read queries.
 *     Returns the LeagueInterfaceImpl so the caller can run cleanup later.
 *
 *   Phase 2 — cleanup()
 *     Deletes the top-level conference, cascading all divisions, teams,
 *     players, and stats rows created in Phase 1.
 *
 * The two phases are deliberately separated so callers (e.g. MultiProcessDemo)
 * can pause between them — showing a dialog that lets you inspect the database
 * before the data is removed.
 *
 * When run standalone via main(), both phases run back-to-back with no pause.
 *
 * Usage:
 *   java -cp <classpath> cluster.RunStatDemo [httpPort] [leagueId]
 *
 * Defaults:
 *   httpPort  = 8080
 *   leagueId  = MLB
 */
public class RunStatDemo {

    private static final String DIVIDER = "  --------------------------------------------------";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Phase 1: Populates the database with real MLB data and runs read queries.
     *
     * League structure created:
     *   Conference: American League
     *     Division:  AL East
     *       Teams:   New York Yankees  |  Boston Red Sox  |  Baltimore Orioles
     *
     * Players registered (10 total, spread across all three teams):
     *   Yankees  — Aaron Judge, Gerrit Cole, Jazz Chisholm Jr.
     *   Red Sox  — Rafael Devers, Jarren Duran, Triston Casas, Garrett Crochet
     *   Orioles  — Gunnar Henderson, Adley Rutschman, Corbin Burnes
     *
     * @param httpPort  HTTP port of the GatewayServer to connect to
     * @param leagueId  League identifier forwarded in every request body
     * @return          The LeagueInterfaceImpl instance used during population,
     *                  ready to be passed to cleanup() later
     */
    public static LeagueInterfaceImpl populate(int httpPort, String leagueId) {
        String host = "localhost";

        LeagueInterfaceImpl league = new LeagueInterfaceImpl(host, httpPort, leagueId);
        FanInterfaceImpl    fan    = new FanInterfaceImpl(host, httpPort, leagueId);

        header("StatCentral Demo", leagueId, httpPort);

        // ── League structure ──────────────────────────────────────────────────
        section("Building league structure");
        show("Conference  \"American League\"",                league.createConference("American League"));
        show("Division    \"AL East\"           (in American League)",
                                                               league.createDivision("AL East", "American League"));
        show("Team        \"New York Yankees\"  (in AL East)", league.createTeam("New York Yankees", "AL East"));
        show("Team        \"Boston Red Sox\"    (in AL East)", league.createTeam("Boston Red Sox",   "AL East"));
        show("Team        \"Baltimore Orioles\" (in AL East)", league.createTeam("Baltimore Orioles","AL East"));

        // ── Player registration ───────────────────────────────────────────────
        // Players must be created after their team exists. Each player is
        // assigned to the team they were on during the 2024 season.

        teamSection("Registering players — New York Yankees");
        show("Aaron Judge         (RF)", league.createPlayer(new PlayerData("Aaron Judge",       "RF", "New York Yankees")));
        show("Gerrit Cole         (SP)", league.createPlayer(new PlayerData("Gerrit Cole",        "SP", "New York Yankees")));
        show("Jazz Chisholm Jr.   (2B)", league.createPlayer(new PlayerData("Jazz Chisholm Jr.", "2B", "New York Yankees")));

        teamSection("Registering players — Boston Red Sox");
        show("Rafael Devers       (3B)", league.createPlayer(new PlayerData("Rafael Devers",   "3B", "Boston Red Sox")));
        show("Jarren Duran        (CF)", league.createPlayer(new PlayerData("Jarren Duran",    "CF", "Boston Red Sox")));
        show("Triston Casas       (1B)", league.createPlayer(new PlayerData("Triston Casas",   "1B", "Boston Red Sox")));
        show("Garrett Crochet     (SP)", league.createPlayer(new PlayerData("Garrett Crochet", "SP", "Boston Red Sox")));

        teamSection("Registering players — Baltimore Orioles");
        show("Gunnar Henderson    (SS)", league.createPlayer(new PlayerData("Gunnar Henderson", "SS", "Baltimore Orioles")));
        show("Adley Rutschman     (C)",  league.createPlayer(new PlayerData("Adley Rutschman",  "C",  "Baltimore Orioles")));
        show("Corbin Burnes       (SP)", league.createPlayer(new PlayerData("Corbin Burnes",    "SP", "Baltimore Orioles")));

        // ── Stat updates ──────────────────────────────────────────────────────
        // atBats is always set before hits so that batting_average is computed
        // against a valid denominator.  Pitchers receive only strikeouts since
        // the AL uses the designated hitter rule (pitchers do not bat).

        teamSection("Updating stats — New York Yankees");

        show("Aaron Judge     atBats  497", league.updatePlayerStat("Aaron Judge",      "atBats",     497));
        show("Aaron Judge     hits    144", league.updatePlayerStat("Aaron Judge",      "hits",       144));
        show("Aaron Judge     homeRuns 58", league.updatePlayerStat("Aaron Judge",      "homeRuns",    58));
        show("Aaron Judge     strikeouts 108", league.updatePlayerStat("Aaron Judge",   "strikeouts", 108));
        System.out.println();

        show("Gerrit Cole     strikeouts 188", league.updatePlayerStat("Gerrit Cole",   "strikeouts", 188));
        System.out.println();

        show("Jazz Chisholm   atBats  419", league.updatePlayerStat("Jazz Chisholm Jr.", "atBats",    419));
        show("Jazz Chisholm   hits    108", league.updatePlayerStat("Jazz Chisholm Jr.", "hits",      108));
        show("Jazz Chisholm   homeRuns 22", league.updatePlayerStat("Jazz Chisholm Jr.", "homeRuns",   22));
        show("Jazz Chisholm   strikeouts 110", league.updatePlayerStat("Jazz Chisholm Jr.", "strikeouts", 110));

        teamSection("Updating stats — Boston Red Sox");

        show("Rafael Devers   atBats  528", league.updatePlayerStat("Rafael Devers",   "atBats",     528));
        show("Rafael Devers   hits    135", league.updatePlayerStat("Rafael Devers",   "hits",       135));
        show("Rafael Devers   homeRuns 22", league.updatePlayerStat("Rafael Devers",   "homeRuns",    22));
        show("Rafael Devers   strikeouts 110", league.updatePlayerStat("Rafael Devers","strikeouts", 110));
        System.out.println();

        show("Jarren Duran    atBats  524", league.updatePlayerStat("Jarren Duran",    "atBats",     524));
        show("Jarren Duran    hits    149", league.updatePlayerStat("Jarren Duran",    "hits",       149));
        show("Jarren Duran    homeRuns 14", league.updatePlayerStat("Jarren Duran",    "homeRuns",    14));
        show("Jarren Duran    strikeouts 116", league.updatePlayerStat("Jarren Duran", "strikeouts", 116));
        System.out.println();

        show("Triston Casas   atBats  130", league.updatePlayerStat("Triston Casas",   "atBats",     130));
        show("Triston Casas   hits     30", league.updatePlayerStat("Triston Casas",   "hits",        30));
        show("Triston Casas   homeRuns  4", league.updatePlayerStat("Triston Casas",   "homeRuns",     4));
        show("Triston Casas   strikeouts 38", league.updatePlayerStat("Triston Casas", "strikeouts",  38));
        System.out.println();

        show("Garrett Crochet strikeouts 209", league.updatePlayerStat("Garrett Crochet", "strikeouts", 209));

        teamSection("Updating stats — Baltimore Orioles");

        show("Gunnar Henderson atBats  560", league.updatePlayerStat("Gunnar Henderson","atBats",     560));
        show("Gunnar Henderson hits    155", league.updatePlayerStat("Gunnar Henderson","hits",       155));
        show("Gunnar Henderson homeRuns 37", league.updatePlayerStat("Gunnar Henderson","homeRuns",    37));
        show("Gunnar Henderson strikeouts 131", league.updatePlayerStat("Gunnar Henderson","strikeouts",131));
        System.out.println();

        show("Adley Rutschman atBats  425", league.updatePlayerStat("Adley Rutschman", "atBats",     425));
        show("Adley Rutschman hits    108", league.updatePlayerStat("Adley Rutschman", "hits",       108));
        show("Adley Rutschman homeRuns 11", league.updatePlayerStat("Adley Rutschman", "homeRuns",    11));
        show("Adley Rutschman strikeouts 81", league.updatePlayerStat("Adley Rutschman","strikeouts",  81));
        System.out.println();

        show("Corbin Burnes   strikeouts 171", league.updatePlayerStat("Corbin Burnes","strikeouts",  171));

        // ── Fan-facing read queries ───────────────────────────────────────────
        // Reads target Aaron Judge only; the aggregate query shows the full
        // AL East home-run leaderboard across all three teams.
        section("Querying stats  (fan interface)");

        System.out.println("  Full stat profile — Aaron Judge:");
        result(fan.getPlayerStats("Aaron Judge"));

        System.out.println("  Home runs — Aaron Judge:");
        result(fan.getPlayerStat("Aaron Judge", "homeRuns"));

        System.out.println("  Top 5 by home runs in AL East  (2026):");
        result(fan.queryTopPlayersByStat("homeRuns", "division", "AL East", 5, 2026));

        return league;
    }

    /**
     * Phase 2: Removes all demo data created by populate().
     *
     * deleteConference("American League") cascades to remove:
     *   AL East → Yankees, Red Sox, Orioles → all 10 players and their stats rows
     */
    public static void cleanup(LeagueInterfaceImpl league) {
        section("Cleaning up demo data");
        show("Removing \"American League\" and all its contents",
                league.deleteConference("American League"));
        System.out.println();
        System.out.println("  Database restored to pre-demo state.");
        footer();
    }

    // ── Standalone entry point ────────────────────────────────────────────────

    /**
     * Runs populate() then cleanup() back-to-back with no pause.
     * For the interactive version with a database-inspection pause, use
     * MultiProcessDemo instead.
     */
    public static void main(String[] args) {
        int    httpPort = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String leagueId = args.length > 1 ? args[1] : "MLB";

        LeagueInterfaceImpl league = populate(httpPort, leagueId);
        cleanup(league);
    }

    // ── Private formatting helpers ────────────────────────────────────────────

    /** Prints the opening banner. */
    private static void header(String title, String leagueId, int httpPort) {
        System.out.println();
        System.out.println("  ====================================================");
        System.out.printf( "    %s  —  %s  (gateway port %d)%n", title, leagueId, httpPort);
        System.out.println("  ====================================================");
        System.out.println();
    }

    /** Prints the closing banner. */
    private static void footer() {
        System.out.println();
        System.out.println("  ====================================================");
        System.out.println("    Demo complete.");
        System.out.println("  ====================================================");
        System.out.println();
    }

    /** Prints a major section divider with a blank line before it. */
    private static void section(String name) {
        System.out.println();
        System.out.println("  " + name);
        System.out.println(DIVIDER);
    }

    /** Prints a lighter per-team sub-header within a section. */
    private static void teamSection(String name) {
        System.out.println();
        System.out.println("    " + name);
    }

    /**
     * Prints one operation's description and the API response on one line.
     * Errors are printed on their own indented line so they stay readable.
     */
    private static void show(String description, String response) {
        if (response != null && response.startsWith("Error")) {
            System.out.println("    " + description);
            System.out.println("      ! " + response);
        } else {
            System.out.printf("    %-44s  %s%n", description, response);
        }
    }

    /**
     * Prints a query result indented beneath its label, with each line of a
     * multi-line response indented uniformly.
     */
    private static void result(String response) {
        if (response == null || response.isBlank()) {
            System.out.println("    (no response)");
        } else {
            for (String line : response.split("\n")) {
                System.out.println("    " + line);
            }
        }
        System.out.println();
    }
}