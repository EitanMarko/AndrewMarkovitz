package com.eitan.productivime.BangForYourBuck;
import com.eitan.productivime.BangForYourBuck.Activities.*;

import java.util.*;

public class BangForYourBuck {

    //User inputs:
        // Activities
        // When the day starts and ends
    //Use dynamic programming to make the most valuable day

    // MUST CREATE SOMETHING WHICH CREATES THE ACTIVITIES FOR THEM TO BE INPUT TO THIS CONSTRUCTOR
        //The @Service will receive a boolean that tells what type of activity it is. Dependent on that, the object is created and sent to the constructor

    private Map<Integer, Activity> setActivityTimes;
    private List<Activity> setActivities;
    private List<Activity> flexActivities;
    private List<Activity> doTodayActivities;
    private Set<String> activityNames;
    private Map<String, Activity> activityMap; // O(1) lookup by name — used for completion tracking

    private List<Interval> filledIntervals;

    private String dayStartStr;
    private String dayEndStr;
    private int dayStart;
    private int dayEnd;

    //NEW IDEA:
        //Don't make the Activities something that are all added at once to the constructor
        //INSTEAD: Create a method thru which you can add Activities to either list


    public BangForYourBuck(String dayStartTime, String dayEndTime) {

        dayStartStr = dayStartTime;
        dayEndStr = dayEndTime;
        dayStart = new Time(dayStartTime).time;
        dayEnd = new Time(dayEndTime).time;


        if(dayStart >= dayEnd){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

        if(dayStart % 5 != 0 || dayEnd % 5 != 0){
            throw new IllegalArgumentException("Day's start and end times must be on a 5-minute interval");
        }
        System.out.println("Day is "+ (dayEnd-dayStart)+" minutes long");

        setActivityTimes = new HashMap<>();
        setActivities = new ArrayList<>();
        flexActivities = new ArrayList<>();
        doTodayActivities = new ArrayList<>();
        activityNames = new HashSet<>();
        activityMap = new HashMap<>();
        filledIntervals = new ArrayList<>();


        //Now that activities have been parsed, we can choose the optimal day


    }

    public boolean addActivity(Activity activity, boolean addMultipleActivities){ // method can be called from user or addMultipleActivities()

        String name = activity.getName();
        if(activityNames.contains(name)){
            throw new IllegalArgumentException("Repeat activity name: "+name);
        }

        if(activity instanceof SetActivity){
            if (!addMultipleActivities) { // If addActivity() called by addMultipleActivities(), below checks already done

                // Invalid interval - starts before day begins || finishes after day ends
                validIntervalCheck((SetActivity) activity); // Ensure activity's interval is within defined "day"
                overlapCheck((SetActivity) activity); // Ensure new SetActivity doesn't overlap previously added SetActivities
            }
            setActivityTimes.put(((SetActivity) activity).getStartTime(), activity); // Mark start time of setActivity for lookup later
            setActivities.add(activity); // Valid interval - add to list
            filledIntervals.add(((SetActivity) activity).getInterval());
        }
        else{ // FlexibleActivity

            if(flexActivities.size() == ((dayEnd - dayStart)/5) ){ // Limit on number of FlexibleActivities is dependent on length of day (5-minute intervals)
                throw new IllegalArgumentException("Limit of "+((dayEnd - dayStart)/5) +" FlexibleActivities has been reached - cannot add more");
            }

            // Invalid interval - insufficient time in day to complete activity
            if(dayEnd - dayStart < ((FlexibleActivity) activity).duration){
                System.out.println("Could not add the activity: "+ activity.getName()); // No exception thrown because addMultipleActivities() may add more activities after this fails
                return false;
            }
            flexActivities.add(activity); // Valid interval - add to list
        }
        activityNames.add(activity.getName()); // Track activity names so no repeats
        activityMap.put(activity.getName(), activity); // Register for O(1) completion lookup
        return true;
    }

    private void overlapCheck(SetActivity activity) {
        for(Interval interval : filledIntervals){

            //If the start time is between the start and end time of the interval
            if(activity.getInterval().start >= interval.start && activity.getInterval().start < interval.end){
                throw new IllegalArgumentException("Attempted to add setActivity which overlaps with previously added setActivity");
            }
            //If the end   time is between the start and end time of the interval
            if(activity.getInterval().end > interval.start && activity.getInterval().end <= interval.end){
                throw new IllegalArgumentException("Attempted to add setActivity which overlaps with previously added setActivity");
            }
        }
    }


    public boolean addMultipleActivities(Activity... activities){

        int nonSetActivities = 0;

        // Check if there are any repeat names among the activities being added
        Set<String> repeats = new HashSet<>();
        for(int i = 0; i < activities.length; i++){
            Activity activity = activities[i];
            String activityName = activity.getName();
            if(activityNames.contains(activityName) || repeats.contains(activityName)){
                throw new IllegalArgumentException("Repeat activity name: " + activityName);
            }
            repeats.add(activityName); // Track new activities in case of repeat names

            if(activity instanceof SetActivity){
                validIntervalCheck((SetActivity) activity); // Ensure activity's interval is within defined "day"
                overlapCheck((SetActivity) activity); // Ensure new SetActivity doesn't overlap previously added SetActivities
            }else{
                nonSetActivities++;
            }
        }

        if((flexActivities.size() + nonSetActivities) > 288){
            throw new IllegalArgumentException("You attempted to add " + nonSetActivities + " activities, but there are only " + (288 - flexActivities.size()) + " available to add before limit");
        }

        boolean allAdded = true;
        for(Activity activity : activities) {
            if(!addActivity(activity, true)){
                allAdded = false;
            }
        }
        return allAdded;
    }

    private void validIntervalCheck(SetActivity activity) {
        // Invalid interval - starts before day begins || finishes after day ends
        if( activity.getInterval().start < dayStart || activity.getInterval().end > dayEnd){
            throw new IllegalArgumentException("SetActivity must be set within daytime hours");
        }
    }

    public ScheduleResult generateSchedule() {

        // Step 1: Sort filled intervals by start time, then build the free intervals between them.
        // Sorting is required because addActivity() accepts SetActivities in any order.
        filledIntervals.sort(Comparator.comparingInt(i -> i.start));

        List<Interval> freeIntervals = new ArrayList<>();
        if (filledIntervals.isEmpty()) {
            // No set activities — the entire day is free
            freeIntervals.add(new Interval(dayStartStr, dayEndStr));
        } else {
            Interval first = filledIntervals.get(0);
            if (first.start > dayStart) {
                freeIntervals.add(new Interval(dayStartStr, first.getStartStr()));
            }
            for (int i = 1; i < filledIntervals.size(); i++) {
                Interval prev = filledIntervals.get(i - 1);
                Interval curr = filledIntervals.get(i);
                if (curr.start > prev.end) {
                    freeIntervals.add(new Interval(prev.getEndStr(), curr.getStartStr()));
                }
            }
            Interval last = filledIntervals.get(filledIntervals.size() - 1);
            if (last.end < dayEnd) {
                freeIntervals.add(new Interval(last.getEndStr(), dayEndStr));
            }
        }

        for (Interval interval : freeIntervals) {
            System.out.println("Free interval: " + interval.getStartStr() + "-" + interval.getEndStr()
                    + " (" + interval.duration + " min)");
        }

        if (flexActivities.isEmpty()) {
            System.out.println("No flexible activities to schedule.");
            List<ScheduleResult.ScheduledInterval> empty = new ArrayList<>();
            for (Interval interval : freeIntervals) {
                empty.add(new ScheduleResult.ScheduledInterval(
                        interval.getStartStr(), interval.getEndStr(), interval.duration, List.of()));
            }
            return new ScheduleResult(empty, 0);
        }

        int N = flexActivities.size();
        int K = freeIntervals.size();

        // Bitmask DP is exact but exponential in N. For N > 20 the memory and runtime
        // cost becomes impractical, so we fall back to a per-interval greedy approximation.
        if (N > 20) {
            System.out.println("Note: more than 20 flexible activities — using greedy approximation.");
            return generateScheduleGreedy(freeIntervals);
        }

        // Step 2: Precompute the total duration and total value of every possible subset
        // of flexible activities so that inner-loop lookups are O(1).
        // Both arrays are built bottom-up: strip the lowest set bit, look up the rest.
        int[] totalDur = new int[1 << N];
        int[] totalVal = new int[1 << N];
        for (int mask = 1; mask < (1 << N); mask++) {
            int lsb  = Integer.numberOfTrailingZeros(mask);
            int rest = mask ^ (1 << lsb);
            totalDur[mask] = totalDur[rest] + flexActivities.get(lsb).getDuration();
            totalVal[mask] = totalVal[rest] + flexActivities.get(lsb).getValue();
        }

        // Step 3: Bitmask DP over all free intervals.
        //
        // dp[mask] = maximum total value achievable by scheduling exactly the activities
        //            indicated by 'mask', distributed across the intervals processed so far.
        //            -1 means this combination cannot be feasibly scheduled.
        //
        // For each interval we try assigning every possible subset of the still-unused
        // activities to it, keeping the assignment that maximises cumulative value.
        // Complexity: O(K * 3^N)  — the subset-enumeration identity gives 3^N total
        // inner iterations per interval pass.
        int[] dp = new int[1 << N];
        Arrays.fill(dp, -1);
        dp[0] = 0;

        // prevMask[j][newMask] = the DP state *before* interval j contributed to newMask.
        // Used during backtracking to recover which activities were assigned to which interval.
        // -1 means interval j contributed no new activities on the path to newMask.
        int[][] prevMask = new int[K][1 << N];
        for (int[] row : prevMask) Arrays.fill(row, -1);

        for (int j = 0; j < K; j++) {
            int capacity = freeIntervals.get(j).duration;
            int[] newDp = dp.clone(); // carry forward all states reachable without using this interval

            for (int used = 0; used < (1 << N); used++) {
                if (dp[used] < 0) continue; // state not yet reachable — skip

                // Enumerate every non-empty subset of the activities not yet scheduled
                int available = ((1 << N) - 1) & ~used;
                for (int sub = available; sub > 0; sub = (sub - 1) & available) {
                    if (totalDur[sub] <= capacity) {
                        int newMask = used | sub;
                        int newVal  = dp[used] + totalVal[sub];
                        if (newVal > newDp[newMask]) {
                            newDp[newMask] = newVal;
                            prevMask[j][newMask] = used; // record how we got here
                        }
                    }
                }
            }
            dp = newDp;
        }

        // Step 4: Find the globally optimal mask — the highest-value feasible assignment.
        int bestMask = 0;
        for (int mask = 1; mask < (1 << N); mask++) {
            if (dp[mask] > dp[bestMask]) bestMask = mask;
        }

        // Step 5: Backtrack through prevMask to recover which activities go in which interval.
        // We walk backwards from the last interval to the first; at each step, the subset
        // assigned to interval j is (currentMask XOR the state before j ran).
        Map<Interval, List<Activity>> scheduleByInterval = new HashMap<>();
        int currentMask = bestMask;
        for (int j = K - 1; j >= 0; j--) {
            List<Activity> assigned = new ArrayList<>();
            int before = prevMask[j][currentMask];
            if (before >= 0) {
                // Interval j contributed some activities — extract which ones
                int subset = currentMask ^ before;
                for (int i = 0; i < N; i++) {
                    if ((subset & (1 << i)) != 0) {
                        assigned.add(flexActivities.get(i));
                    }
                }
                currentMask = before;
            }
            scheduleByInterval.put(freeIntervals.get(j), assigned);
        }

        // Step 6: Build the result and print the optimal schedule in chronological order
        System.out.println("\n=== OPTIMAL SCHEDULE ===");
        int totalValue = 0;
        List<ScheduleResult.ScheduledInterval> resultIntervals = new ArrayList<>();
        for (Interval interval : freeIntervals) {
            List<Activity> assigned = scheduleByInterval.get(interval);
            System.out.println("\n" + interval.getStartStr() + "-" + interval.getEndStr()
                    + " (" + interval.duration + " min):");
            List<ScheduleResult.ScheduledActivity> resultActivities = new ArrayList<>();
            if (assigned == null || assigned.isEmpty()) {
                System.out.println("  (no activities scheduled)");
            } else {
                int timeUsed = 0;
                for (Activity a : assigned) {
                    System.out.println("  - " + a.getName()
                            + " (" + a.getDuration() + " min, value " + a.getValue() + ")");
                    totalValue += a.getValue();
                    timeUsed  += a.getDuration();
                    resultActivities.add(new ScheduleResult.ScheduledActivity(
                            a.getName(), a.getDuration(), a.getValue()));
                }
                System.out.println("  Time used: " + timeUsed + "/" + interval.duration + " min");
            }
            resultIntervals.add(new ScheduleResult.ScheduledInterval(
                    interval.getStartStr(), interval.getEndStr(), interval.duration, resultActivities));
        }
        System.out.println("\nTotal schedule value: " + totalValue);
        return new ScheduleResult(resultIntervals, totalValue);
    }

    // Greedy fallback used when N > 20. Processes intervals smallest-to-largest and
    // runs a standard 0/1 knapsack per interval. Correct within each interval but
    // not guaranteed globally optimal across multiple intervals.
    private ScheduleResult generateScheduleGreedy(List<Interval> freeIntervals) {
        List<Interval> sorted = new ArrayList<>(freeIntervals);
        sorted.sort(Comparator.comparingInt(i -> i.duration));

        List<Activity> allFlex = new ArrayList<>(flexActivities);
        allFlex.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        boolean[] used = new boolean[allFlex.size()];
        Map<Interval, List<Activity>> scheduleByInterval = new HashMap<>();

        for (Interval interval : sorted) {
            int capacity = interval.duration;
            List<Activity> available = new ArrayList<>();
            List<Integer> indices   = new ArrayList<>();
            for (int i = 0; i < allFlex.size(); i++) {
                if (!used[i]) { available.add(allFlex.get(i)); indices.add(i); }
            }
            if (available.isEmpty()) {
                scheduleByInterval.put(interval, new ArrayList<>());
                continue;
            }

            int m = available.size();
            int[][] dpTable = new int[m + 1][capacity + 1];
            for (int i = 1; i <= m; i++) {
                int w = available.get(i - 1).getDuration();
                int v = available.get(i - 1).getValue();
                for (int c = 0; c <= capacity; c++) {
                    dpTable[i][c] = dpTable[i - 1][c];
                    if (w <= c) dpTable[i][c] = Math.max(dpTable[i][c], dpTable[i - 1][c - w] + v);
                }
            }

            List<Activity> selected = new ArrayList<>();
            int c = capacity;
            for (int i = m; i > 0 && c > 0; i--) {
                if (dpTable[i][c] != dpTable[i - 1][c]) {
                    selected.add(available.get(i - 1));
                    used[indices.get(i - 1)] = true;
                    c -= available.get(i - 1).getDuration();
                }
            }
            scheduleByInterval.put(interval, selected);
        }

        System.out.println("\n=== OPTIMAL SCHEDULE (greedy approximation) ===");
        int totalValue = 0;
        List<ScheduleResult.ScheduledInterval> resultIntervals = new ArrayList<>();
        for (Interval interval : freeIntervals) {
            List<Activity> assigned = scheduleByInterval.get(interval);
            System.out.println("\n" + interval.getStartStr() + "-" + interval.getEndStr()
                    + " (" + interval.duration + " min):");
            List<ScheduleResult.ScheduledActivity> resultActivities = new ArrayList<>();
            if (assigned == null || assigned.isEmpty()) {
                System.out.println("  (no activities scheduled)");
            } else {
                int timeUsed = 0;
                for (Activity a : assigned) {
                    System.out.println("  - " + a.getName()
                            + " (" + a.getDuration() + " min, value " + a.getValue() + ")");
                    totalValue += a.getValue();
                    timeUsed  += a.getDuration();
                    resultActivities.add(new ScheduleResult.ScheduledActivity(
                            a.getName(), a.getDuration(), a.getValue()));
                }
                System.out.println("  Time used: " + timeUsed + "/" + interval.duration + " min");
            }
            resultIntervals.add(new ScheduleResult.ScheduledInterval(
                    interval.getStartStr(), interval.getEndStr(), interval.duration, resultActivities));
        }
        System.out.println("\nTotal schedule value: " + totalValue);
        return new ScheduleResult(resultIntervals, totalValue);
    }


    private Activity findActivity(int index){ // Finds activity in imaginary contiguous list of activities (excluding setActivities)

        if(index < doTodayActivities.size()){ // DoTodayFlexActivity
            return doTodayActivities.get(index);
        }
        else{ // FlexibleActivity
            return flexActivities.get(index - doTodayActivities.size());
        }
    }

    // -------------------------------------------------------------------------
    // Completion Tracking
    // -------------------------------------------------------------------------

    /**
     * Marks the activity with the given name as completed.
     * This is the single entry point for recording that a task has been done.
     *
     * @param name The exact name of the activity to mark complete.
     * @throws IllegalArgumentException if no activity with that name exists.
     */
    public void completeActivity(String name) {
        Activity activity = activityMap.get(name);
        if (activity == null) {
            throw new IllegalArgumentException("No activity found with name: \"" + name + "\"");
        }
        activity.markComplete();
        System.out.println("✓ \"" + name + "\" marked as completed.");
    }

    /**
     * Returns whether the activity with the given name has been completed.
     *
     * @param name The exact name of the activity to query.
     * @throws IllegalArgumentException if no activity with that name exists.
     */
    public boolean isActivityCompleted(String name) {
        Activity activity = activityMap.get(name);
        if (activity == null) {
            throw new IllegalArgumentException("No activity found with name: \"" + name + "\"");
        }
        return activity.isCompleted();
    }

    // -------------------------------------------------------------------------
    // Day Effectiveness
    // -------------------------------------------------------------------------

    /**
     * Calculates how effective the day was as a value between 0.0 and 1.0.
     *
     * Effectiveness = (sum of scoring values of completed activities)
     *               / (sum of scoring values of ALL activities)
     *
     * Note: FlexibleActivity.getScoringValue() always returns the user-assigned
     * priority (1-10) even if doToday() has been called, so the percentage
     * reflects meaningful user-defined priorities rather than scheduling
     * sentinel values.
     *
     * @return A double in [0.0, 1.0], or 0.0 if no activities have been added.
     */
    public double getDayEffectiveness() {
        int totalValue = 0;
        int completedValue = 0;

        for (Activity activity : activityMap.values()) {
            int score = activity.getScoringValue();
            totalValue += score;
            if (activity.isCompleted()) {
                completedValue += score;
            }
        }

        if (totalValue == 0) {
            return 0.0;
        }
        return (double) completedValue / totalValue;
    }

    /**
     * Prints a human-readable summary of the day's effectiveness to stdout.
     * Shows per-activity status as well as the overall percentage.
     */
    public void printDaySummary() {
        System.out.println("\n=== DAY EFFECTIVENESS SUMMARY ===");

        int totalValue = 0;
        int completedValue = 0;

        // Print in the natural grouping order: set → doToday → flex
        printActivityGroupSummary("Set Activities",       setActivities);
        printActivityGroupSummary("Do-Today Activities",  doTodayActivities);
        printActivityGroupSummary("Flexible Activities",  flexActivities);

        for (Activity activity : activityMap.values()) {
            int score = activity.getScoringValue();
            totalValue += score;
            if (activity.isCompleted()) completedValue += score;
        }

        double pct = totalValue == 0 ? 0.0 : (double) completedValue / totalValue * 100;
        System.out.printf("%nOverall: %d / %d points  →  %.1f%% effective%n", completedValue, totalValue, pct);
    }

    private void printActivityGroupSummary(String groupLabel, List<Activity> activities) {
        if (activities.isEmpty()) return;
        System.out.println("\n" + groupLabel + ":");
        for (Activity activity : activities) {
            String status = activity.isCompleted() ? "✓" : "✗";
            System.out.printf("  %s  %s  (value: %d)%n", status, activity.getName(), activity.getScoringValue());
        }
    }

    public List<Activity> getFlexActivities() {
        return flexActivities;
    }

    public List<Activity> getDoTodayActivities() {
        return doTodayActivities;
    }

    public List<Activity> getSetActivities() {
        return setActivities;
    }
}