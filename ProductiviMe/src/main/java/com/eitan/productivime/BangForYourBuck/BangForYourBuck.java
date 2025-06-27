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
    private int nonSetActivityCount;
    private Set<String> activityNames;
    private List<Interval> filledIntervals;

    private int dayStart;
    private int dayEnd;
    private Schedule optimalSchedule;
    private List<Schedule> tiedSchedules;

    //NEW IDEA:
        //Don't make the Activities something that are all added at once to the constructor
        //INSTEAD: Create a method thru which you can add Activities to either list


    public BangForYourBuck(String dayStartTime, String dayEndTime) {

        dayStart = new Time(dayStartTime).time;
        dayEnd = new Time(dayEndTime).time;

        if(dayStart >= dayEnd){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

        setActivityTimes = new HashMap<>();
        setActivities = new ArrayList<>();
        flexActivities = new ArrayList<>();
        doTodayActivities = new ArrayList<>();
        nonSetActivityCount = 0;
        activityNames = new HashSet<>();
        filledIntervals = new ArrayList<>();
        optimalSchedule = new Schedule(0,0,0,0, 0);
        tiedSchedules = new ArrayList<>();
        tiedSchedules.add(optimalSchedule); // tiedSchedules should never have 0 Schedules in it


        //Now that activities have been parsed, we can choose the optimal day


    }

    private class Schedule {

        private int value;
        private int time;
        private int numOfActivities;
        private long activitiesDone;
        private int lastActivityIndex; //For backtracking - indicates the last activity done. Index is in the list of activities (whether actually or virtually contiguous)
        private int thisActivityIndex;
        private int setActivitiesDone;

        public Schedule(int value, long activitiesDone, int lastActivityIndex, int thisActivityIndex, int time) {
            this.value = value;
            this.time = time;
            this.numOfActivities = 0;
            this.activitiesDone = activitiesDone;
            this.lastActivityIndex = lastActivityIndex;
            this.thisActivityIndex = thisActivityIndex;
            this.setActivitiesDone = 0;
        }

        public void setNumOfActivities(int numOfActivities) {
            this.numOfActivities = numOfActivities;
        }

        public int getNumOfActivities() {
            return numOfActivities;
        }

        public long getActivitiesDone() {
            return activitiesDone;
        }

        public int getValue() {
            return value;
        }

        public int getLastActivityIndex() {
            return lastActivityIndex;
        }

        public void setSetActivitiesDone(int setActivitiesDone) {
            this.setActivitiesDone = setActivitiesDone;
        }

        public int getSetActivitiesDone() {
            return setActivitiesDone;
        }
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
            setActivityTimes.put(activity.getLatestStartTime(), activity); // Mark start time of setActivity for lookup later
            setActivities.add(activity); // Valid interval - add to list
            filledIntervals.add(((SetActivity) activity).interval);
        }
        else{ // FlexibleActivity or DoTodayFlexActivity

            if(nonSetActivityCount == 64){
                throw new IllegalArgumentException("Limit of 64 non-setActivities has been reached - cannot add more");
            }

            int begin;
            int end;
            boolean isFlexibleActivity = isFlexibleActivity(activity);

            if(isFlexibleActivity){
                begin = ((FlexibleActivity) activity).startBy;
                end = ((FlexibleActivity) activity).endBy;
            } else{ // DoTodayFlexActivity
                begin = ((DoTodayFlexActivity) activity).startBy;
                end = ((DoTodayFlexActivity) activity).endBy;
            }

            if(begin < dayStart){
                begin = dayStart; // Activity cannot begin before dayStart
            }

            if(end>dayEnd){
                end = dayEnd; // Activity cannot end after dayEnd
            }

            // Invalid interval - insufficient time in day to complete activity
            if (isFlexibleActivity) {
                if(end - begin < ((FlexibleActivity) activity).duration){
                    System.out.println("Could not add the activity: "+ activity.getName()); // No exception thrown because addMultipleActivities() may add more activities after this fails
                    return false;
                }
                flexActivities.add(activity); // Valid interval - add to list
            }
            else { // DoTodayFlexActivity
                if(end - begin < ((DoTodayFlexActivity) activity).duration){
                    System.out.println("Could not add the activity: "+ activity.getName()); // No exception thrown because addMultipleActivities() may add more activities after this fails
                    return false;
                }
                doTodayActivities.add(activity); // Valid interval - add to list
            }
            nonSetActivityCount++; // An activity which is not a setActivity has been added
        }
        activityNames.add(activity.getName()); // Track activity names so no repeats
        return true;
    }

    private void overlapCheck(SetActivity activity) {
        for(Interval interval : filledIntervals){

            //If the start time is between the start and end time of the interval
            if(activity.interval.start >= interval.start && activity.interval.start < interval.end){
                throw new IllegalArgumentException("Attempted to add setActivity which overlaps with previously added setActivity");
            }
            //If the end   time is between the start and end time of the interval
            if(activity.interval.end > interval.start && activity.interval.end <= interval.end){
                throw new IllegalArgumentException("Attempted to add setActivity which overlaps with previously added setActivity");
            }
        }
    }

    private boolean isFlexibleActivity(Activity activity){
        if(activity instanceof FlexibleActivity){
            return true;
        }
        return false;
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

        if((nonSetActivityCount + nonSetActivities) > 64){
            throw new IllegalArgumentException("You attempted to add " + nonSetActivities + " activities, but there are " + (64 - nonSetActivityCount) + " available to add before limit");
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
        if( activity.interval.start < dayStart || activity.interval.end > dayEnd){
            throw new IllegalArgumentException("SetActivity must be set within daytime hours");
        }
    }

    public List<Schedule> generateSchedule(){

        Schedule emptySchedule = new Schedule(0,0, -1, -1,0);
        int numOfActivities = setActivities.size() + doTodayActivities.size() + flexActivities.size();
        int intervalStartTimes = (dayEnd - dayStart) / 5; //Activities can only occur at times of factor 5 (e.g. 1:00, 1:05, 1:10, etc.)
        Schedule[][] dp = new Schedule[intervalStartTimes][numOfActivities];

        for(int i = 0; i < numOfActivities; i++){
            dp[0][i] = emptySchedule;
        }


        //BFS - look at all possibilities, beginning with the empty schedule

        ArrayList<Schedule> pq = new ArrayList<>();
        pq.add(emptySchedule);
        while(!pq.isEmpty()){
            // Create all possible new Schedules out of that Schedule

            // We want to bit wise & 1 with all places of the Double
                // 64 places
            // num & 1
            // num >> 1
            // num & 1
            // Bit shift 63 times

            // FIRST CHECK FOR A SETACTIVITY AT THIS TIME
                //Make a system that can efficiently search for whether or not there's a setActivity now
                    // Map setActivities to times - then search for those times when you're looking for it

            Schedule prevSchedule = pq.remove(0); // Get next schedule
            long activitiesDone = prevSchedule.activitiesDone;

            if(setActivityTimes.get(prevSchedule.time) != null){
                // TWO OPTIONS:

                    // DOING THIS!!!!!!!!!!!!!
                    // Change the impl so there's only a map holding the setActivities
                    // Each schedule tracks how many setActivities have been done
                    // You can check whether or not all setActivities have been done by comparing to setActivityTimes.size()
                        // An activity will never be double counted bc an activity only has one opportunity to get checked off
                    // THIS REQUIRES CHANGING THE findActivity() method

                    // Check the map for whether or not there is a setActivity to do now
                    // If yes, choose it and then do O(n) search thru list to "check it off" bitwise
                    // If no, don't choose it
                    // (n^2)
                        // Doing O(n) thru all activities for all n activities

                Activity thisSetActivity = setActivityTimes.get(prevSchedule.time);
                int newValue = prevSchedule.value + thisSetActivity.getValue();
                int newTime = prevSchedule.time + thisSetActivity.getDuration();

                // Set lastActivityIndex and thisActivityIndex the same because there is no index for a setActivity, and so should backtrack to last flex of doToday activity
                Schedule newSchedule = new Schedule(newValue, activitiesDone, prevSchedule.thisActivityIndex, prevSchedule.thisActivityIndex, newTime);
                newSchedule.setSetActivitiesDone(prevSchedule.getSetActivitiesDone()+1); // Record how many setActivities have been done
                newSchedule.setNumOfActivities(prevSchedule.getNumOfActivities() + 1); // Record how many Activities were done overall
                pq.add(newSchedule);

                continue; // Schedule with the setActivity is the only viable Schedule, so stop generating
            }

            boolean addedSchedule = false;

            // IF NO SETACTIVITY AT THIS TIME:

            if((activitiesDone & 1) == 0){ // if first activity is available and not a setActivity

                Activity activityZero = findActivity(0);
                int newTime = prevSchedule.time + activityZero.getDuration();

                if(newTime <= dayEnd){ // Create new Schedule if new Schedule stays within bounds of the day
                    int newValue = prevSchedule.value + activityZero.getValue();
                    long updatedActivitiesDone = activitiesDone ^ 1;
                    Schedule newSchedule = new Schedule(newValue, updatedActivitiesDone, prevSchedule.thisActivityIndex, 0, newTime); // Create new Schedule with activity added
                    newSchedule.setSetActivitiesDone(prevSchedule.getSetActivitiesDone()); // Record how many setActivities have been done
                    newSchedule.setNumOfActivities(prevSchedule.getNumOfActivities() + 1); // Record how many Activities were done overall
                    pq.add(newSchedule);
                    addedSchedule = true; // At least one more Schedule generated
                }
            }
            for(int i = 0; i < 63; i++){
                long activityNumPlace = activitiesDone >> (i+1); // Bit-shift to place of activity we're attempting to add to schedule
                if((activityNumPlace & 1) == 0) { // available activity

                    Activity thisActivity = findActivity(i+1);
                    int newTime = prevSchedule.time + thisActivity.getDuration();

                    if(newTime <= dayEnd){ // Create new Schedule if new Schedule stays within bounds of the day
                        int newValue = prevSchedule.value + thisActivity.getValue();
                        long updatedActivitiesDone = activitiesDone ^ (1L <<(i+1)); // Mark activity as completed
                        Schedule newSchedule = new Schedule(newValue, updatedActivitiesDone, prevSchedule.thisActivityIndex, i+1, newTime); // Create new Schedule with activity added
                        newSchedule.setSetActivitiesDone(prevSchedule.getSetActivitiesDone()); // Record how many setActivities have been done
                        newSchedule.setNumOfActivities(prevSchedule.getNumOfActivities() + 1); // Record how many Activities were done overall
                        pq.add(newSchedule);
                        addedSchedule = true; // At least one more Schedule generated
                    }
                }
            }
            if(!addedSchedule){ // If no Schedules could be generated, see if this one is the most valuable

                //What the check includes:
                    // 1) Are all setActivities completed in this schedule?
                if(prevSchedule.setActivitiesDone < setActivityTimes.size()){
                    continue;
                }
                    // 2) Are all doTodayFlexActivities completed in this schedule?
                        // To check #2, bitwise '&' Node.activitiesDone with positions of all setActivities and doTodayFlexActivities
                int doTodayActsNum = doTodayActivities.size();
                if(doTodayActsNum != 0){
                    long flexBits = 1L << 63; // Move 1 into top bit position, so you can do a right arithmatic shift
                    int shift = doTodayActsNum - 1; // Will right shift this many bits
                    flexBits = flexBits >> shift; // Now 1s should cover every position of DoTodayFlexActivity bits (e.g. 11110000)
                    flexBits = ~flexBits; // Flip the bits so now all FlexibleActivity-corresponding bits are 1s
                    long isoTodayBits = activitiesDone ^ flexBits; // Now all FlexibleActivity bits are actually turned on
                    long shouldBeZero = ~isoTodayBits; // Flip all bits. If all DoTodayFlexActivity bits were on, then all bits were 1, and are now 0
                    if(shouldBeZero != 0){ // If not all bits are now 0, not all DoTodayFlexActivities were completed, and Schedule is invalid
                        continue;
                    }

                }
                // else- no doTodayActivities, no problem and no reason to check

                    // 3) Is the value of the Node greater than "int greatestValue"?
                        // greatestValue is instantiated at 0
                        // If Schedule.value > greatestValue, it's a candidate
                        // If Schedule.value == greatestValue, we will do a TIEBREAKER (COME BACK TO THIS) - What makes a schedule more valuable than another?

                if(prevSchedule.value > optimalSchedule.value){
                    optimalSchedule = prevSchedule;
                    tiedSchedules = new ArrayList<>(); // Reset tiedSchedules
                    tiedSchedules.add(prevSchedule);
                }
                if(prevSchedule.value == optimalSchedule.value){
                    // TIEBREAKER:
                        // Number of activities done
                    // If tiebreaker is the same, add both schedules to tiedSchedules

                    if(prevSchedule.getNumOfActivities() > optimalSchedule.getNumOfActivities()){
                        optimalSchedule = prevSchedule;
                        tiedSchedules = new ArrayList<>(); // Reset tiedSchedules
                        tiedSchedules.add(prevSchedule);
                    }
                    else if (prevSchedule.getNumOfActivities() == optimalSchedule.getNumOfActivities()){
                        tiedSchedules.add(prevSchedule);
                    }
                    //else - num of activities is less - optimalSchedule is still optimal so do nothing

                }

            }
        }

        return tiedSchedules; // If >1 Schedule in List, program should give the user the options

        // NEXT: FIND ACTUAL ORDER OF ACTIVITIES IN SCHEDULE
        // ALSO: SHOULD THERE BE BREAKS IN A SCHEDULE?






        //Create a two dimensional array -> [time][activity]

        //Let's say that an activity has to be done at a time which is a multiple of 5
            // Create a Time instance, divide the value by 5
                // Ex: instead of 0:30 being 30, it's 6 because:
                    // 0, 5, 10, 15, 20, 25, 30 (6th term in the sequence)
            // Index:  0, 1,  2,  3,  4,  5    INDEX 6
        // PROGRAM CAN BE MADE MORE EFFICIENT WITH LONGER INTERVALS (e.g. 15 mins), BUT THEN USER LOSES FLEXIBILTY
            // More efficient because much smaller array

        //In the array slot we store an object which holds two things:
            // Current value
            // Double to indicate completed activities ("activitiesDone")

        //There will be a List to store these activities in set indices - SEE "ACTIVITIES LIST" BELOW

        //To generate the next activity in your day (among possibilities):
            // Check - Is there is a setActivity slated for that time (e.g. we're at 12:00 and setActivity "lunch" set for 12:00)?
                // IF YES - Do that setActivity
                //If NO:
                    // take the activitiesDone (long) and bitwise '&' with position of activity (for all activities)
                        // If bit is not set, activity has not yet been done, and you can move forward in adding this to a schedule
                // Why: Because if we don't do a setActivity, the Schedule we're creating will be thrown out anyway (non-viable Schedule)
                    // Elminiates unnecessary work (efficient)
                //I THINK THIS DOUBLE ENABLES AT MOST 64 ACTIVITIES TO BE INPUT. IF THIS IS THE CASE,
                        // MAKE A CHECK IN addActivity() which caps the number of activities being added

        // ACTIVITIES LIST - 2 options:
            // 1) Don't make another list of activities, just keep the 3 lists, and consider them as one
                // Ex: if there are 2 setActivities but we look for index 2 among ALL activities,
                //      that will end up in index 0 of the doTodayActivities list
                // ORDER OF VIRTUAL CONTIGUOUS LIST: setActivities, doTodayActivities, flexActivities
            // 2) Make another list of activities, and when an activity is added to the specific list, add it also to the general list
                // Ex: Adding a setActivity "eat"
                    // Add "eat" to setActivities list
                    // Add "eat" to allActivities list


      //  ------------------------------------

        //Generate possibilities by BFS

        //If a given possibility is impossible because it would take you beyond the end of the day, don't generate it

        //If a Schedule can't generate any further activities
        // (e.g. day ends at 6:00, it's 5:45, and no activities take <=15 mins),
        //Make a check on that Schedule

        //What the check includes:
            // 1) Is the value of the Node greater than "int greatestValue"?
                // greatestValue is instantiated at 0
                // If Schedule.value > greatestValue, it's a candidate
                // If Schedule.value == greatestValue, we will do a TIEBREAKER (COME BACK TO THIS) - What makes a schedule more valuable than another?
            // 2) Are all setActivities completed in this schedule?
            // 3) Are all doTodayFlexActivities completed in this schedule?
                // To check #2,3, bitwise '&' Node.activitiesDone with positions of all setActivities and doTodayFlexActivities
        //IF THESE CONDITIONS PASS, SAVE THE NODE AS THE CURRENT BEST SCHEDULE
        //WHEN BFS ENDS, WHATEVER IS SAVED IS THE FINAL SCHEDULE


        // --------------------------------------------

        // FOR LATER - Create a checks in addActivity() which don't allow:
        //      a setActivity to be added if it overlaps with another setActivity
        //      a doTodayFlexActivity to be added if there is no available space for this activity to occur based on
        //          previously input setActivities and doTodayFlexActivities
            // Reasoning: because then no schedule will be viable

        // Ex: Day is 10:00-12:00
            // Adding setActivity 10:00-11:00 - ACCEPTED
            // Adding setActivity 10:30-10:45 - REJECTED
            // Adding setActivity 11:30-12:00 - ACCEPTED
            // Adding doTodayFlexActivity 15 mins - ACCEPTED
            // Adding doTodayFlexActivity 45 mins - REJECTED
        //THIS MAY BE VERY HARD TO DO BECAUSE OF CERTAIN SCENARIOS (e.g. Adding a setActivity after many doTodayFlexActivities have been added)
            // SO MAYBE JUST DO THIS FOR setActivities

        //TO GET ACTUAL SCHEDULE - BACKTRACKING
            // A 'Schedule' object holds an 'int lastActivityIndex' which indicates the index
            //   in the list of activities (virtually or actually contiguous) of the previous activity
            // 'int lastActivityTime' = find the duration of the last activity, subtract that from the current time
            // To backtrack traverse back to dp[lastActivityTime][lastActivityIndex]
                //If lastActivityIndex == -1, this indicates first activity in schedule (end of backtracking)

    }


    private Activity findActivity(int index){ // Finds activity in imaginary contiguous list of activities (excluding setActivities)

        if(index < doTodayActivities.size()){ // DoTodayFlexActivity
            return doTodayActivities.get(index);
        }
        else{ // FlexibleActivity
            return flexActivities.get(index - doTodayActivities.size());
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
