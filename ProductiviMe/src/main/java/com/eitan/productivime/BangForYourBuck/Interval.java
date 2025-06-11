package com.eitan.productivime.BangForYourBuck;

public class Interval {

    public int start;
    public int end;
    public int duration;
    public Interval(String startTime, String endTime) {
        this.start = new Time(startTime).time;
        this.end = new Time(endTime).time;

        if(start >= end){
            throw new IllegalArgumentException("end must be later than start (24-hr clock)");
        }

        this.duration = end - start;
    }
}
