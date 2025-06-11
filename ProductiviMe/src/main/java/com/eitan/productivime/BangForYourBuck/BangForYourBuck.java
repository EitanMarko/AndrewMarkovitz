package com.eitan.productivime.BangForYourBuck;

import java.util.ArrayList;
import java.util.List;

public class BangForYourBuck {

    //User inputs:
        // Activities
        // When the day starts and ends
    //Use dynamic programming to make the most valuable day

    // MUST CREATE SOMETHING WHICH CREATES THE ACTIVITIES FOR THEM TO BE INPUT TO THIS CONSTRUCTOR
        //The @Service will receive a boolean that tells what type of activity it is. Dependent on that, the object is created and sent to the constructor

    private List<Activity> setActivities;
    private List<Activity> flexActivities;

    private int dayStart;
    private int dayEnd;

    //NEW IDEA:
        //Don't make the Activities something that are all added at once to the constructor
        //INSTEAD: Create a method thru which you can add Activities to either list


    public BangForYourBuck(String dayStartTime, String dayEndTime, Activity... activities) {

        dayStart = new Time(dayStartTime).time;
        dayEnd = new Time(dayEndTime).time;

        if(dayStart >= dayEnd){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

        setActivities = new ArrayList<>();
        flexActivities = new ArrayList<>();

        //If an activity has an interval (even flexible) which falls outside the bounds of
            // dayStart-dayEnd ("the day"), what do we do?

        //For a set activity, the activity is thrown out
        //For a flexible activity, only take into account the portion of the activity which falls within the day
            //If there's not enough overlap between the interval and the day hours, throw out the activity


        for(Activity activity : activities){
            if(activity instanceof SetActivity){

                // Invalid interval - starts before day begins || finishes after day ends
                if( ((SetActivity) activity).interval.start < dayStart || ((SetActivity) activity).interval.end > dayEnd){
                    continue;
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
                    continue;
                }
                flexActivities.add(activity); // Valid interval - add to list

            }

        }


        //Now that activities have been parsed, we can choose the optimal day


    }

    public List<Activity> getFlexActivities() {
        return flexActivities;
    }

    public List<Activity> getSetActivities() {
        return setActivities;
    }
}
