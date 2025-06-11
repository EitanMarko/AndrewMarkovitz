package com.eitan.productivime.BangForYourBuck;

public class Activity {

    //Each activity has fields:
    //Start by (optional)
    //End by (optional)
    //Interval (optional)
    //Duration
    //Value (?/10)
    public String name;
                                                                    //long? (duration)
    public Activity(String name, String startBy, String endBy, Interval interval, long duration, int value) {
        //Use the Time class to derive a value from the startBy & endBy strings
        //Or just do this is in a private method?
        this.name = name;
        Time start = new Time(startBy);
        Time end = new Time(endBy);

    }
}
