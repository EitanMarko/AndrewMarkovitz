package com.eitan.productivime.BangForYourBuck.Activities;
import com.eitan.productivime.BangForYourBuck.*;

public class SetActivity implements Activity, Comparable<Activity> {

    public String name;
    public Interval interval;


    public SetActivity(String name, String startTime, String endTime) {
        this.name = name;
        this.interval = new Interval(startTime, endTime);
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
    public int getEndTime(){
        return interval.end;
    }

    public void setStartTime(String start){
        interval.setStart(start);
    }

    public void setEndTime(String end){
        interval.setEnd(end);
    }

    public Interval getInterval(){
        return this.interval;
    }

    //getInterval() method - to use when creating free blocks
        // Every time a SetActivity is created, get its interval, and add it to an ordered list
}
