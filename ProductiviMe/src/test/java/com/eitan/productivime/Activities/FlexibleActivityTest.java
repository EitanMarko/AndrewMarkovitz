package com.eitan.productivime.Activities;
import com.eitan.productivime.BangForYourBuck.Activities.Activity;
import com.eitan.productivime.BangForYourBuck.Activities.FlexibleActivity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FlexibleActivityTest {

    @Test
    void simpleFlexActivity() {

        Activity activity = new FlexibleActivity("flex", "10:30","17:00","2:00", 7);

        assertEquals("flex",activity.getName());
        assertEquals(7,activity.getValue());

        int duration = 2 * 60; // 2 hours == 120 mins
        int end = 17 * 60; // 17:00 is (17 * 60) mins
        assertEquals(end-duration,activity.getLatestStartTime());

    }


}
