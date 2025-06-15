package com.eitan.productivime.Activities;
import com.eitan.productivime.BangForYourBuck.Activities.Activity;
import com.eitan.productivime.BangForYourBuck.Activities.DoTodayFlexActivity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DoTodayFlexActivityTest {

    @Test
    void simpleDoTodayFlexActivity() {

        Activity activity = new DoTodayFlexActivity("doToday", "10:30","17:00","2:00");

        assertEquals("doToday",activity.getName());
        assertEquals(11,activity.getValue());

        int duration = 2 * 60; // 2 hours == 120 mins
        int end = 17 * 60; // 17:00 is (17 * 60) mins
        assertEquals(end-duration,activity.getLatestStartTime());

    }
}
