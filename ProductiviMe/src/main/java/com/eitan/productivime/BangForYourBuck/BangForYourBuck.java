package com.eitan.productivime.BangForYourBuck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BangForYourBuck {

    //User inputs:
        // Activities
        // When the day starts and ends
    //Use dynamic programming to make the most valuable day

    // MUST CREATE SOMETHING WHICH CREATES THE ACTIVITIES FOR THEM TO BE INPUT TO THIS CONSTRUCTOR
        //The @Service will receive a boolean that tells what type of activity it is. Dependent on that, the object is created and sent to the constructor

    private List<Activity> setActivities;
    private List<Activity> flexActivities;
    private Set<String> activityNames;

    private int dayStart;
    private int dayEnd;

    //NEW IDEA:
        //Don't make the Activities something that are all added at once to the constructor
        //INSTEAD: Create a method thru which you can add Activities to either list


    public BangForYourBuck(String dayStartTime, String dayEndTime) {

        dayStart = new Time(dayStartTime).time;
        dayEnd = new Time(dayEndTime).time;

        if(dayStart >= dayEnd){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

        setActivities = new ArrayList<>();
        flexActivities = new ArrayList<>();
        activityNames = new HashSet<>();


        //Now that activities have been parsed, we can choose the optimal day


    }


    //If an activity has an interval (even flexible) which falls outside the bounds of
        // dayStart-dayEnd ("the day"), what do we do?

    //For a set activity, the activity is thrown out
    //For a flexible activity, only take into account the portion of the activity which falls within the day
        //If there's not enough overlap between the interval and the day hours, throw out the activity


    public void addActivity(Activity activity){

        String name = activity.getName();
        if(activityNames.contains(name)){
            throw new IllegalArgumentException("Repeat activity name: "+name);
        }

        if(activity instanceof SetActivity){
            // Invalid interval - starts before day begins || finishes after day ends
            if( ((SetActivity) activity).interval.start < dayStart || ((SetActivity) activity).interval.end > dayEnd){
                return;
            }
            setActivities.add(activity); // Valid interval - add to list
        }
        else{ // FlexibleActivity
            int begin = ((FlexibleActivity) activity).startBy;
            if(begin < dayStart){
                begin = dayStart; // Earliest start of activity at dayStart
            }

            int end = ((FlexibleActivity) activity).endBy;
            if(end>dayEnd){
                end = dayEnd; // Latest finish of activity at dayEnd
            }

            // Invalid interval - insufficient time in day to complete activity
            if(end - begin < ((FlexibleActivity) activity).duration){
                return;
            }
            flexActivities.add(activity); // Valid interval - add to list
        }
        activityNames.add(activity.getName()); // Track activity names so no repeats
    }

    public void addMultipleActivities(Activity... activities){

        // Check if there are any repeat names among the activities being added
        Set<String> repeats = new HashSet<>();
        for(int i = 0; i<activities.length; i++){
            String activityName = activities[i].getName();
            if(activityNames.contains(activityName) || repeats.contains(activityName)){
                throw new IllegalArgumentException("Repeat activity name: "+activityName);
            }
            repeats.add(activityName);
        }

        for(Activity activity : activities) {
            addActivity(activity);
        }
    }

    public List<Activity> getFlexActivities() {
        return flexActivities;
    }

    public List<Activity> getSetActivities() {
        return setActivities;
    }
}
