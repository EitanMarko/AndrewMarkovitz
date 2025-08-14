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

    public void generateSchedule(){

        // STEP 1: GET ALL FREE INTERVALS

        // SORT filledIntervals HERE????????????????????????????

        List<Interval> freeIntervals = new ArrayList<>();
        if(!filledIntervals.isEmpty()){
            Interval firstInterval = filledIntervals.get(0);
            if(firstInterval.start > dayStart){ // From start of day til start of first interval
                freeIntervals.add(new Interval(dayStartStr,firstInterval.getStartStr()));
            }
        }
        for(int i = 1; i < filledIntervals.size(); i++){
            // Q: Would it be faster to instead just do an O(n) pass thru all Intervals in filledIntervals,
                // and create a "Free Interval" between each?
                //Case: if difference between Intervals == 0, DO NOT create "Free Interval".
                    // Seems simpler to me...

            // Add dif between start of day and first interval
            Interval prev = filledIntervals.get(i-1);
            Interval curr = filledIntervals.get(i);
            if(curr.start - prev.end != 0){
                freeIntervals.add(new Interval(prev.getEndStr(),curr.getStartStr()));
            }
        }

        if (!filledIntervals.isEmpty()) {
            Interval lastInterval = filledIntervals.get(filledIntervals.size()-1);
            if(lastInterval.end < dayEnd){ // From end of last interval til end of day
                freeIntervals.add(new Interval(lastInterval.getEndStr(), dayEndStr));
            }
        }

        for(Interval interval : freeIntervals){
            System.out.println("Free Interval: "+ interval.getStartStr()+"-"+interval.getEndStr());
        }

        // STEP 2: DP BUCKET FILL EACH FREE INTERVAL
            // SMALLEST -> LARGEST INTERVAL ORDER








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
