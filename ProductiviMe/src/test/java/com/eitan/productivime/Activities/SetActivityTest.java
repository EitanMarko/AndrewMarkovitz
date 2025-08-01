package com.eitan.productivime.Activities;

import com.eitan.productivime.BangForYourBuck.Activities.Activity;
import com.eitan.productivime.BangForYourBuck.Activities.SetActivity;
import com.eitan.productivime.BangForYourBuck.Interval;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SetActivityTest {

    @Test
    void simpleSetActivity() {

        Interval interval = new Interval("10:30","11:00");
        Activity activity = new SetActivity("set", interval);

        assertEquals("set",activity.getName());
        assertEquals(12,activity.getValue());
    }
}
