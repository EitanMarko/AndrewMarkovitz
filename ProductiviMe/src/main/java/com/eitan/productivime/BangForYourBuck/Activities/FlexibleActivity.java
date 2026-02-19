package com.eitan.productivime.BangForYourBuck.Activities;
import com.eitan.productivime.BangForYourBuck.*;

public class FlexibleActivity implements Activity, Comparable<Activity>{

    public String name;
    public int duration;
    private int flexValue; // Save most recently set value between 1-10 in case activity is accidentally set to doToday()
    private int value;
    private boolean isDoTodayActivity;
    private boolean completed;
    public FlexibleActivity(String name, String durationTime, int value) {

        this.name = name;
        this.duration = new Time(durationTime).time;
        if(this.duration % 5 != 0){
            throw new IllegalArgumentException("Duration must be a multiple of 5");
        }

        if(value < 1 || value > 10){ // Value scaled 1-10
            throw new IllegalArgumentException("Activities are valued within priority 1-10");
        }
        this.value = value;
        this.flexValue = value;
        this.isDoTodayActivity = false;
        this.completed = false;


    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getValue() {
        return value;
    }
    public void setValue(int value){

        if(value < 1 || value > 10){ // Value scaled 1-10
            throw new IllegalArgumentException("Activities are valued within priority 1-10");
        }

        this.value = value;
        this.flexValue = value;
    }

    @Override
    public int getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        Time newDuration = new Time(duration);
        if(newDuration.time % 5 != 0){
            throw new IllegalArgumentException("Duration must be a multiple of 5");
        }
        this.duration = newDuration.time;
    }
    // set to a value which:
        //1) is greater than all regular FlexibleActivities combined (at their highest priority)
        //2) does not overflow Integer.MAX_VALUE when as many as possible are added

    // 1) 24 hours x 12 slots per hour (60 minutes/5 minutes) = 288
        // 288 possible activities x 10 highest priority = 2,880 total priority

    // 2) Assign DoTodayActivity a priority of 3,000 (nice round number)
        // 3,000 x 288 = 864,000 (much less than Integer.MAX_VALUE - 2,147,483,647)
    public void doToday(){
        this.value = 3000;
        this.isDoTodayActivity = true;

    }

    public void makeFlexible(){
        this.value = this.flexValue; // Reset to the most recently set value between 1-10
        this.isDoTodayActivity = false;
    }

    public boolean isDoTodayActivity(){
        return isDoTodayActivity;
    }

    @Override
    public int compareTo(Activity other) {
        return Integer.compare(this.value, other.getValue());
    }
    @Override
    public void markComplete() {
        this.completed = true;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the real user-assigned priority (1-10), stripping out any doToday()
     * inflation. This keeps day-effectiveness percentages meaningful: a doToday
     * activity worth 7 to the user counts as 7, not 3000.
     */
    @Override
    public int getScoringValue() {
        return flexValue;
    }
}
