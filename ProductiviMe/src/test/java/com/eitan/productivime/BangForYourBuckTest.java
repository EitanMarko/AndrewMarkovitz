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

        // Both start and end out of bounds
        Activity act1 = new SetActivity("act1","8:45","17:15");

        // Only end out of bounds
        Activity act2 = new SetActivity("act2","9:00","17:15");

        // Only start out of bounds
        Activity act3 = new SetActivity("act3","8:45","17:00");

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

        // Interval in bounds
        Activity act4 = new SetActivity("act4","9:00","11:00");
        Activity act5 = new SetActivity("act5","11:30","14:15");

        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00");
        bangForYourBuckFull.addActivity(act4, false);
        bangForYourBuckFull.addActivity(act5, false);

        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        assertEquals(nonEmptyList,bangForYourBuckFull.getSetActivities());


        Activity act6 = new SetActivity("act6","14:45","15:00");
        Activity act7 = new SetActivity("act7","15:15","15:30");
        Activity act8 = new SetActivity("act8","15:45","16:00");


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

        // Valid interval
        Activity act1 = new SetActivity("act1","10:00","12:00");

        // interval falls within (overlaps) act1's interval
        Activity act2 = new SetActivity("act2","11:00","11:30");

        BangForYourBuck bangForYourBuck = new BangForYourBuck("6:00","22:00");
        bangForYourBuck.addActivity(act1, false);
        activityList.add(act1);

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act2, false);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(act2);});

        // Keep track of setActivity intervals, and for a given add, check whether there is overlap

        // interval overlaps act1's interval
        Activity act3 = new SetActivity("act3","9:00","11:00");
        Activity act4 = new SetActivity("act4","11:00","13:00");

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act3, false);});

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addActivity(act4, false);});


        //Activities may be assigned back-to-back (e.g. 10:00-12:00 exists, can also add 9:00-10:00 and 12:00-13:00)

        // Add Activities individually


        Activity act5 = new SetActivity("act5","9:00","10:00");
        Activity act6 = new SetActivity("act6","12:00","13:00");

        bangForYourBuck.addActivity(act5, false);
        bangForYourBuck.addActivity(act6, false);
        activityList.add(act5);
        activityList.add(act6);

        // Now 9:00-13:00 is reserved for setActivities
        assertEquals(activityList,bangForYourBuck.getSetActivities());


        // Add Activities all at once

        Activity act7 = new SetActivity("act7","8:00","9:00");
        Activity act8 = new SetActivity("act8","13:00","14:00");

        bangForYourBuck.addMultipleActivities(act7,act8);
        activityList.add(act7);
        activityList.add(act8);

        // Now 8:00-14:00 is reserved for setActivities
        assertEquals(activityList,bangForYourBuck.getSetActivities());
    }

    @Test
    void flexibleActivities() {


        // INVALID ACTIVITIES

        // Duration of activities are all >8, and therefore cannot fit in this day (of 8 hours)
        Activity act1 = new FlexibleActivity("act1", "9:00", 10); // 9 hours
        Activity act2 = new FlexibleActivity("act2", "10:00", 10); // 10 hours
        Activity act3 = new FlexibleActivity("act3","11:00", 10); // 11 hours

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckEmpty.addMultipleActivities(act1, act2, act3);
        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getFlexActivities()); //No activities were added



        // VALID ACTIVITIES

        // Duration of activities are all <8, and therefore fit in this day (of 8 hours)
        Activity act4 = new FlexibleActivity("act4","3:00", 10);
        Activity act5 = new FlexibleActivity("act5","1:30", 10);
        Activity act6 = new FlexibleActivity("act6","4:15", 10);

        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00"); // 8 hour day
        bangForYourBuckFull.addMultipleActivities(act4, act5, act6);
        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        nonEmptyList.add(act6);
        assertEquals(nonEmptyList, bangForYourBuckFull.getFlexActivities());

    }


    @Test
    void invalidDayTimes() {

        // dayStart >= dayEnd
        assertThrows(IllegalArgumentException.class, () -> {
            BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","8:00");});
        assertThrows(IllegalArgumentException.class, () -> {
            BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","9:00");});

        // Day's start and end times must on a 5-minute interval
        assertThrows(IllegalArgumentException.class, () -> {
            BangForYourBuck bangForYourBuck1 = new BangForYourBuck("9:01","10:00");});
        assertThrows(IllegalArgumentException.class, () -> {
            BangForYourBuck bangForYourBuck1 = new BangForYourBuck("9:00","10:01");});
    }

    @Test
    void repeatActivities() { // Two activities with the same name - only the first one is added

        //Adding repeat activities together

        Activity act1 = new FlexibleActivity("activity","2:00", 10);
        Activity act2 = new SetActivity("activity", "12:00","13:00");

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00");

        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(act1,act2);});


        //Adding activities separately

        BangForYourBuck bangForYourBuck2 = new BangForYourBuck("9:00","20:00");

        bangForYourBuck2.addActivity(act2, false);
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addActivity(act1, false);});


        //Repeat of activity already added

        Activity act3 = new FlexibleActivity("thing","2:00", 10);
        Activity act4 = new FlexibleActivity("thing","2:00", 10);

        BangForYourBuck bangForYourBuck3 = new BangForYourBuck("9:00","20:00");
        bangForYourBuck3.addActivity(act3, false);


        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck3.addMultipleActivities(act1, act3);});

    }

    @Test
    void FlexibleActivityLimit() {

        // Add exactly the right number of FlexibleActivities individually
        BangForYourBuck bangForYourBuck0 = new BangForYourBuck("9:00","20:00");
        String name0 = "nam";
        Time nine = new Time("9:00");
        Time twenty = new Time("20:00");
        for(int i = 0; i < (twenty.time - nine.time)/5 ; i++){
            name0 += "e";
            FlexibleActivity newAct = new FlexibleActivity(name0,"0:30",10);
            if(i % 2 == 0){
                newAct.doToday(); // This includes DoTodayActivities
            }
            bangForYourBuck0.addActivity(newAct,false);
        }
        FlexibleActivity extraAct = new FlexibleActivity(name0,"0:30",10);
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck0.addActivity(extraAct,false);}); // Adding one extra FlexibleActivity (over limit)


        // Add one too many FlexibleActivities all at once
        BangForYourBuck bangForYourBuck = new BangForYourBuck("3:00","22:00");
        Time three = new Time("3:00");
        Time twentyTwo = new Time("22:00");
        int numOfSlots = (twentyTwo.time - three.time)/5; // Number of allowed activities to be added
        Activity[] activities = new Activity[numOfSlots+1];
        String name = "nam";
        for(int i = 0; i < numOfSlots+1; i++){
            name += "e";
            FlexibleActivity newAct = new FlexibleActivity(name,"0:30",10);
            if(i % 2 == 0){
                newAct.doToday(); // This includes DoTodayActivities
            }
            activities[i] = newAct;
        }
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck.addMultipleActivities(activities);}); // Adding too many FlexibleActivities at once


        // Add exactly the right number of FlexibleActivities individually (safe), then one more (over limit)
        BangForYourBuck bangForYourBuck2 = new BangForYourBuck("7:00","18:00");
        Time seven = new Time("7:00");
        Time eighteen = new Time("18:00");
        int numOfSlots2 = (eighteen.time - seven.time)/5; // Number of allowed activities to be added
        Activity[] activities2 = new Activity[numOfSlots2];
        String name2 = "nam";
        for(int i = 0; i < numOfSlots2; i++){
            name2 += "e";
            FlexibleActivity newAct = new FlexibleActivity(name2,"0:30",10);
            if(i % 2 == 0){
                newAct.doToday(); // This includes DoTodayActivities
            }
            activities2[i] = newAct;
        }
        bangForYourBuck2.addMultipleActivities(activities2); // Add up to the limit of FlexibleActivities

        name2 += "e";
        Activity newAct = new FlexibleActivity(name2,"0:30", 10);

        // Attempt to add another FlexibleActivity - Invalid
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addMultipleActivities(newAct);});
        assertThrows(IllegalArgumentException.class, () -> {
            bangForYourBuck2.addActivity(newAct, false);});

    }

    @Test
    void failedActivityAdds() {

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","20:00"); // 11 hours in the "day"

        Activity activity = new FlexibleActivity("activity","18:00", 10); // Duration: 18 hours
        assertFalse(bangForYourBuck.addActivity(activity, false));
        System.out.print("\n"); // Separate print statements for separate asserts

        Activity activity2 = new FlexibleActivity("activity2", "18:00", 10); // Duration: 18 hours
        Activity activity3 = new FlexibleActivity("activity3","18:00", 10); // Duration: 18 hours

        Activity validActivity = new FlexibleActivity("validActivity","1:00", 10); // Duration: 1 hour
        Activity validActivity2 = new FlexibleActivity("validActivity2","1:00", 10); // Duration: 1 hour

        assertTrue(bangForYourBuck.addActivity(validActivity, false)); // Test that an Activity with these parameter is valid (then use validActivity2 in next test)

        bangForYourBuck.addMultipleActivities(activity2, activity3, validActivity2); // Only activity2 and activity3 should print as unadded activities
    }


    @Test
    void generator() {

        BangForYourBuck bangForYourBuck = new BangForYourBuck("9:00","17:00"); // 11 hours in the "day"
        // Free Interval: 9:00-10:00
        Activity act1 = new SetActivity("act1", "10:00", "11:00");
        // Free Interval: 11:00-12:00
        Activity act2 = new SetActivity("act2", "12:00", "13:00");
        // Free Interval: 13:00-14:00
        Activity act3 = new SetActivity("act3", "14:00", "15:00");
        Activity act4 = new SetActivity("act4", "15:00", "16:00");
        // Free Interval: 16:00-17:00 (end of day)

        // Try generating before any setActivities are added
        bangForYourBuck.generateSchedule();

        bangForYourBuck.addMultipleActivities(act1,act2, act3,act4);
        bangForYourBuck.generateSchedule();

        System.out.println("--------------------------------------------------");

        //SetActivity starts at beginning of day
        BangForYourBuck bangForYourBuck2 = new BangForYourBuck("9:00","17:00"); // 11 hours in the "day"
        Activity act5 = new SetActivity("act5", "9:00", "10:00");
        bangForYourBuck2.addActivity(act5,false);
        bangForYourBuck2.generateSchedule();

        System.out.println("--------------------------------------------------");

        //SetActivity ends at end of day
        BangForYourBuck bangForYourBuck3 = new BangForYourBuck("9:00","17:00"); // 11 hours in the "day"
        Activity act6 = new SetActivity("act6", "16:00", "17:00");
        bangForYourBuck3.addActivity(act6,false);
        bangForYourBuck3.generateSchedule();



    }
}
