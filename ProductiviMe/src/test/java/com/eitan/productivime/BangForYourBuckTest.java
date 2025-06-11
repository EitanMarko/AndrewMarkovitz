package com.eitan.productivime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.eitan.productivime.BangForYourBuck.*;

import java.util.ArrayList;
import java.util.List;

public class BangForYourBuckTest {

    @Test
    void setActivities() {

        // INVALID INTERVALS

        Interval interval1 = new Interval("8:45","17:15"); // Both start and end out of bounds
        Activity act1 = new SetActivity("act1",interval1,10);

        Interval interval2 = new Interval("9:00","17:15");  // Only end out of bounds
        Activity act2 = new SetActivity("act2",interval2,10);

        Interval interval3 = new Interval("8:45","17:00"); // Only start out of bounds
        Activity act3 = new SetActivity("act3",interval3,10);

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00", act1, act2, act3);

        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getSetActivities()); //No activities were added


        // VALID INTERVALS

        Interval interval4 = new Interval("9:00","17:00"); // Only start out of bounds
        Activity act4 = new SetActivity("act4",interval4,10);

        Interval interval5 = new Interval("11:30","14:15"); // Only start out of bounds
        Activity act5 = new SetActivity("act5",interval5,10);

        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00", act4, act5);


        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        assertEquals(nonEmptyList,bangForYourBuckFull.getSetActivities());
    }

    @Test
    void flexibleActivities() {


        // INVALID INTERVALS


        // Flexible interval shortened to 9:00-15:00, which is < 7 hours to complete the activity
        Activity act1 = new FlexibleActivity("act1","5:00","15:00", "7:00");

        // Flexible interval shortened to 15:00-17:00, which is < 4 hours to complete the activity
        Activity act2 = new FlexibleActivity("act2","15:00","22:00", "4:00");

        // Flexible interval shortened to 9:00-17:00, which is < 10 hours to complete the activity
        Activity act3 = new FlexibleActivity("act3","5:00","22:00", "10:00");

        BangForYourBuck bangForYourBuckEmpty = new BangForYourBuck("9:00","17:00",act1, act2, act3); // 8 hour day
        List<Activity> emptyList= new ArrayList<>();
        assertEquals(emptyList, bangForYourBuckEmpty.getFlexActivities()); //No activities were added



        // VALID INTERVALS


        // Flexible interval shortened to 9:00-15:00, which is > 3 hours to complete the activity
        Activity act4 = new FlexibleActivity("act4","5:00","15:00", "3:00");

        // Flexible interval shortened to 15:00-17:00, which is > 1.5 hours to complete the activity
        Activity act5 = new FlexibleActivity("act5","15:00","22:00", "1:30");

        // Flexible interval shortened to 9:00-17:00, which is > 4.25 hours to complete the activity
        Activity act6 = new FlexibleActivity("act6","5:00","22:00", "4:15");

        // Flexible interval unshortened, 10:00-11:00 is > 0:30 to complete activity
        Activity act7 = new FlexibleActivity("act7","10:00","11:00", "0:30");


        BangForYourBuck bangForYourBuckFull = new BangForYourBuck("9:00","17:00",act4, act5, act6, act7); // 8 hour day
        List<Activity> nonEmptyList= new ArrayList<>();
        nonEmptyList.add(act4);
        nonEmptyList.add(act5);
        nonEmptyList.add(act6);
        nonEmptyList.add(act7);
        assertEquals(nonEmptyList, bangForYourBuckFull.getFlexActivities());

    }
}
