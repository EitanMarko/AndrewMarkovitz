package com.eitan.productivime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.eitan.productivime.BangForYourBuck.*;
import com.eitan.productivime.BangForYourBuck.Activities.*;

import java.util.ArrayList;
import java.util.List;

public class BangForYourBuckTest {

    @Test
    void setActivities() {

        // INVALID INTERVALS

            // Add Activities individually

        Interval interval1 = new Interval("8:45","17:15"); // Both start and end out of bounds
        Activity act1 = new SetActivity("act1",interval1);

        Interval interval2 = new Interval("9:00","17:15");  // Only end out of bounds
        Activity act2 = new SetActivity("act2",interval2);

        Interval interval3 = new Interval("8:45","17:00"); // Only start out of bounds
        Activity act3 = new SetActivity("act3",interval3);

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00");

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuckEmpty.addActivity(act1, false);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuckEmpty.addActivity(act2, false);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuckEmpty.addActivity(act3, false);});

        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getSetActivities()); //No activities were added


            // Add Activities all at once

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuckEmpty.addMultipleActivities(act1, act2, act3);});
        assertEquals(emptyList, bangForYourBuckEmpty.getSetActivities()); //No activities were added


        // VALID INTERVALS

            // Add Activities individually

        Interval interval4 = new Interval("9:00","11:00"); // Interval in bounds
        Activity act4 = new SetActivity("act4",interval4);

        Interval interval5 = new Interval("11:30","14:15"); // Interval in bounds
        Activity act5 = new SetActivity("act5",interval5);

        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00");
        bangForYourBuckFull.addActivity(act4, false);
        bangForYourBuckFull.addActivity(act5, false);

        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        assertEquals(nonEmptyList,bangForYourBuckFull.getSetActivities());


        Interval interval6 = new Interval("14:45","15:00");
        Activity act6 = new SetActivity("act6",interval6);

        Interval interval7 = new Interval("15:15","15:30");
        Activity act7 = new SetActivity("act7",interval7);

        Interval interval8 = new Interval("15:45","16:00");
        Activity act8 = new SetActivity("act8",interval8);


            // Add Activities all at once

        bangForYourBuckFull.addMultipleActivities(act6, act7, act8);

        nonEmptyList.add(act6);
        nonEmptyList.add(act7);
        nonEmptyList.add(act8);

        assertEquals(nonEmptyList,bangForYourBuckFull.getSetActivities());
    }

    @Test
    void overlappingIntervals() { // SetActivities may not overlap

        List<Activity> activityList= new ArrayList<>(); // List of viable activities

        Interval interval1 = new Interval("10:00","12:00"); // Valid interval
        Activity act1 = new SetActivity("act1",interval1);

        Interval interval2 = new Interval("11:00","11:30");  // Interval falls within (overlaps) interval1
        Activity act2 = new SetActivity("act2",interval2);

        BangForYourBuck bangForYourBuck = new BangForYourBuck("6:00","22:00");
        bangForYourBuck.addActivity(act1, false);
        activityList.add(act1);

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act2, false);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(act2);});

        // Keep track of setActivity intervals, and for a given add, check whether there is overlap

        Interval interval3 = new Interval("9:00","11:00"); // Interval overlaps interval1
        Activity act3 = new SetActivity("act3",interval3);

        Interval interval4 = new Interval("11:00","13:00"); // Interval overlaps interval1
        Activity act4 = new SetActivity("act4",interval4);

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act3, false);});

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act4, false);});


        //Activities may be assigned back-to-back (e.g. 10:00-12:00 exists, can also add 9:00-10:00 and 12:00-13:00)

        // Add Activities individually

        Interval interval5 = new Interval("9:00","10:00");
        Activity act5 = new SetActivity("act5",interval5);

        Interval interval6 = new Interval("12:00","13:00");
        Activity act6 = new SetActivity("act6",interval6);

        bangForYourBuck.addActivity(act5, false);
        bangForYourBuck.addActivity(act6, false);
        activityList.add(act5);
        activityList.add(act6);

        // Now 9:00-13:00 is reserved for setActivities
        assertEquals(activityList,bangForYourBuck.getSetActivities());


        // Add Activities all at once

        Interval interval7 = new Interval("8:00","9:00");
        Activity act7 = new SetActivity("act7",interval7);

        Interval interval8 = new Interval("13:00","14:00");
        Activity act8 = new SetActivity("act8",interval8);

        bangForYourBuck.addMultipleActivities(act7,act8);
        activityList.add(act7);
        activityList.add(act8);

        // Now 8:00-14:00 is reserved for setActivities
        assertEquals(activityList,bangForYourBuck.getSetActivities());
    }

    @Test
    void flexibleActivities() {


        // INVALID INTERVALS


        // Flexible interval shortened to 9:00-15:00, which is < 7 hours to complete the activity
        Activity act1 = new FlexibleActivity("act1","5:00","15:00", "7:00", 10);

        // Flexible interval shortened to 15:00-17:00, which is < 4 hours to complete the activity
        Activity act2 = new FlexibleActivity("act2","15:00","22:00", "4:00", 10);

        // Flexible interval shortened to 9:00-17:00, which is < 10 hours to complete the activity
        Activity act3 = new FlexibleActivity("act3","5:00","22:00", "10:00", 10);

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckEmpty.addMultipleActivities(act1, act2, act3);
        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getFlexActivities()); //No activities were added



        // VALID INTERVALS


        // Flexible interval shortened to 9:00-15:00, which is > 3 hours to complete the activity
        Activity act4 = new FlexibleActivity("act4","5:00","15:00", "3:00", 10);

        // Flexible interval shortened to 15:00-17:00, which is > 1.5 hours to complete the activity
        Activity act5 = new FlexibleActivity("act5","15:00","22:00", "1:30", 10);

        // Flexible interval shortened to 9:00-17:00, which is > 4.25 hours to complete the activity
        Activity act6 = new FlexibleActivity("act6","5:00","22:00", "4:15", 10);

        // Flexible interval unshortened, 10:00-11:00 is > 0:30 to complete activity
        Activity act7 = new FlexibleActivity("act7","10:00","11:00", "0:30", 10);


        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckFull.addMultipleActivities(act4, act5, act6, act7);
        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        nonEmptyList.add(act6);
        nonEmptyList.add(act7);
        assertEquals(nonEmptyList, bangForYourBuckFull.getFlexActivities());

    }

    @Test
    void doTodayActivities() {


        // INVALID INTERVALS


        // DoTodayFlexActivity interval shortened to 9:00-15:00, which is < 7 hours to complete the activity
        Activity act1 = new DoTodayFlexActivity("act1","5:00","15:00", "7:00");

        // DoTodayFlexActivity interval shortened to 15:00-17:00, which is < 4 hours to complete the activity
        Activity act2 = new DoTodayFlexActivity("act2","15:00","22:00", "4:00");

        // DoTodayFlexActivity interval shortened to 9:00-17:00, which is < 10 hours to complete the activity
        Activity act3 = new DoTodayFlexActivity("act3","5:00","22:00", "10:00");

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckEmpty.addMultipleActivities(act1, act2, act3);
        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getDoTodayActivities()); //No activities were added



        // VALID INTERVALS


        // DoTodayFlexActivity interval shortened to 9:00-15:00, which is > 3 hours to complete the activity
        Activity act4 = new DoTodayFlexActivity("act4","5:00","15:00", "3:00");

        // DoTodayFlexActivity interval shortened to 15:00-17:00, which is > 1.5 hours to complete the activity
        Activity act5 = new DoTodayFlexActivity("act5","15:00","22:00", "1:30");

        // DoTodayFlexActivity interval shortened to 9:00-17:00, which is > 4.25 hours to complete the activity
        Activity act6 = new DoTodayFlexActivity("act6","5:00","22:00", "4:15");

        // DoTodayFlexActivity interval unshortened, 10:00-11:00 is > 0:30 to complete activity
        Activity act7 = new DoTodayFlexActivity("act7","10:00","11:00", "0:30");


        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckFull.addMultipleActivities(act4, act5, act6, act7);
        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        nonEmptyList.add(act6);
        nonEmptyList.add(act7);
        assertEquals(nonEmptyList, bangForYourBuckFull.getDoTodayActivities());

    }

    @Test
    void noTimeInDay() { // Cases where there is insufficient time in the day to perform the activity, so it is not added

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00"); // 11 hours in the "day"

        Activity activity = new FlexibleActivity("activity","1:00", "20:00", "18:00", 10); // Duration: 18 hours
        assertFalse(bangForYourBuck.addActivity(activity, false));

        Activity activity2 = new DoTodayFlexActivity("activity","1:00", "20:00", "18:00"); // Duration: 18 hours
        assertFalse(bangForYourBuck.addActivity(activity2, false));

    }

    @Test
    void repeatActivities() { // Two activities with the same name - only the first one is added

        //Adding repeat activities together

        Activity act1 = new FlexibleActivity("activity","13:00","17:00","2:00", 10);

        Interval interval = new Interval("12:00","13:00");
        Activity act2 = new SetActivity("activity", interval);

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00");

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(act1,act2);});


        //Adding activities separately

        BangForYourBuck bangForYourBuck2 = new BangForYourBuck("9:00","20:00");

        bangForYourBuck2.addActivity(act2, false);
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addActivity(act1, false);});


        //Repeat of activity already added

        Activity act3 = new FlexibleActivity("thing","13:00","17:00","2:00", 10);
        Activity act4 = new FlexibleActivity("thing","13:00","17:00","2:00", 10);

        BangForYourBuck bangForYourBuck3 = new BangForYourBuck("9:00","20:00");
        bangForYourBuck3.addActivity(act3, false);


        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck3.addMultipleActivities(act1, act3);});




    }

    @Test
    void nonSetActivityLimit() {

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00");
        Activity[] activities = new Activity[65];
        String name = "nam";
        for(int i = 0; i < 65; i++){
            name += "e";
            Activity newAct = new DoTodayFlexActivity(name,"10:00", "12:00", "0:30");
            activities[i] = newAct;
        }
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(activities);}); // Adding too many non-setActivities at once


        BangForYourBuck bangForYourBuck2 = new BangForYourBuck("9:00","20:00");
        Activity[] activities2 = new Activity[64];
        String name2 = "nam";
        for(int i = 0; i < 64; i++){
            name2 += "e";
            Activity newAct = new DoTodayFlexActivity(name2,"10:00", "12:00", "0:30");
            activities2[i] = newAct;
        }
        bangForYourBuck2.addMultipleActivities(activities2); // Add up to the limit (64) of non-setActivities

        name2 += "e";
        Activity newAct = new FlexibleActivity(name2,"10:00", "12:00", "0:30", 10);

        // Attempt to add another non-setActivity - Invalid (over the limit i.e. 64)
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addMultipleActivities(newAct);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addActivity(newAct, false);});

    }

    @Test
    void failedActivityAdds() {

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00"); // 11 hours in the "day"

        Activity activity = new FlexibleActivity("activity","1:00", "20:00", "18:00", 10); // Duration: 18 hours
        assertFalse(bangForYourBuck.addActivity(activity, false));
        System.out.print("\n"); // Separate print statements for separate asserts

        Activity activity2 = new DoTodayFlexActivity("activity2","1:00", "20:00", "18:00"); // Duration: 18 hours
        Activity activity3 = new FlexibleActivity("activity3","1:00", "20:00", "18:00", 10); // Duration: 18 hours

        Activity validActivity = new FlexibleActivity("validActivity","11:00", "13:00", "1:00", 10); // Duration: 1 hour
        Activity validActivity2 = new FlexibleActivity("validActivity2","11:00", "13:00", "1:00", 10); // Duration: 1 hour

        assertTrue(bangForYourBuck.addActivity(validActivity, false)); // Test that an Activity with these parameter is valid (then use validActivity2 in next test)

        bangForYourBuck.addMultipleActivities(activity2, activity3, validActivity2); // Only activity2 and activity3 should print as unadded activities
    }
}
