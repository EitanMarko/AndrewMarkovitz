package com.eitan.productivime.BangForYourBuck.Activities;

import com.eitan.productivime.BangForYourBuck.Time;

public class DoTodayFlexActivity implements Activity{

    public String name;
    public int startBy;
    public int endBy;
    public int duration;
    private int latestStartTime;
    public DoTodayFlexActivity(String name, String startByTime, String endByTime, String durationTime) {

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

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getValue() {
        return 11;
    }

    @Override
    public int getLatestStartTime() {
        return latestStartTime;
    }
}
