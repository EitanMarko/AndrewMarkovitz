package com.eitan.productivime.BangForYourBuck.Activities;

public interface Activity {

    String getName();
    int getValue();
    int getDuration();
    /**
     * Marks this activity as completed.
     * Once marked complete, calling this again has no effect (idempotent).
     */
    void markComplete();

    /**
     * Returns whether this activity has been marked as completed.
     */
    boolean isCompleted();

    /**
     * Returns the value of this activity as it should be counted toward
     * day-effectiveness scoring. For most activities this is the same as
     * getValue(), but subclasses may override (e.g. FlexibleActivity inflates
     * its value when doToday() is called — the scoring value strips that out).
     */
    int getScoringValue();

}
