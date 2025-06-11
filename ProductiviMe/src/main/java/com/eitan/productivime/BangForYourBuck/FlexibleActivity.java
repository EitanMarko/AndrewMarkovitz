package com.eitan.productivime.BangForYourBuck;

public class FlexibleActivity implements Activity{

    public String name;
    public int startBy;
    public int endBy;
    public int duration;
    public FlexibleActivity(String name,String startByTime, String endByTime, String durationTime) {

       this.startBy = new Time(startByTime).time;
       this.endBy = new Time(endByTime).time;
       this.duration = new Time(durationTime).time;

        if(startBy >= endBy){
            throw new IllegalArgumentException("dayEndTime must be later than dayStartTime (24-hr clock)");
        }

       if(endBy - startBy < duration){ // if the activity was longer than the day
           throw new IllegalArgumentException("Duration cannot exceed flexible interval allotted to perform activity");
       }

       this.name = name;

    }
}
