package com.eitan.productivime.BangForYourBuck.Activities;
import com.eitan.productivime.BangForYourBuck.*;

import java.util.Optional;

public class SetActivity implements Activity, Comparable<Activity> {

    //Each activity has fields:
    //Start by (optional)
    //End by (optional)
    //Interval (optional)
    //Duration
    //Value (?/10)
    public String name;
    public Interval interval;

                                                                    //long? (duration)
    public SetActivity(String name, Interval interval) {
        //Use the Time class to derive a value from the startBy & endBy strings
        //Or just do this is in a private method?
        this.name = name;
        this.interval = interval;


    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getValue() {
        return 12;
    }

    @Override
    public int getDuration() {
        return interval.duration;
    }

    @Override
    public int compareTo(Activity other) {
        return Integer.compare(12, other.getValue());
    }

    public int getStartTime(){
        return interval.start;
    }
}
