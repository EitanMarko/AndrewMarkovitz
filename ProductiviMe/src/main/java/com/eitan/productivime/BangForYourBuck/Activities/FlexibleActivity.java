package com.eitan.productivime.BangForYourBuck.Activities;
import com.eitan.productivime.BangForYourBuck.*;

public class FlexibleActivity implements Activity, Comparable<Activity>{

    public String name;
    public int startBy;
    public int endBy;
    public int duration;
    private int latestStartTime;
    private int value;
    public FlexibleActivity(String name, String startByTime, String endByTime, String durationTime, int value) {

        this.name = name;
        this.startBy = new Time(startByTime).time;
        this.endBy = new Time(endByTime).time;
        this.duration = new Time(durationTime).time;
        this.latestStartTime = endBy - duration;

        if(startBy >= endBy){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

       if(endBy - startBy < duration){ // if the activity was longer than the day
           throw new IllegalArgumentException("Duration cannot exceed flexible interval allotted to perform activity");
       }
        if(value < 1 || value > 10){ // Value scaled 1-10
            throw new IllegalArgumentException("Activities are valued within priority 1-10");
        }
        this.value = value;


    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Override
    public int getLatestStartTime() {
        return latestStartTime;
    }

    @Override
    public int compareTo(Activity other) {
        return Integer.compare(this.value, other.getValue());
    }
}
