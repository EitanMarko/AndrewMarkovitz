package com.eitan.productivime.BangForYourBuck;

import java.util.Optional;

public class SetActivity implements Activity{

    //Each activity has fields:
    //Start by (optional)
    //End by (optional)
    //Interval (optional)
    //Duration
    //Value (?/10)
    public String name;
    public Interval interval;
    private int value;

                                                                    //long? (duration)
    public SetActivity(String name, Interval interval, int value) {
        //Use the Time class to derive a value from the startBy & endBy strings
        //Or just do this is in a private method?
        this.name = name;
        this.interval = interval;

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
}
