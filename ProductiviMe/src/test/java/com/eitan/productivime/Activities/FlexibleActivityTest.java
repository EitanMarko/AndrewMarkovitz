package com.eitan.productivime.Activities;
import com.eitan.productivime.BangForYourBuck.Activities.Activity;
import com.eitan.productivime.BangForYourBuck.Activities.FlexibleActivity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FlexibleActivityTest {

    @Test
    void simpleFlexActivity() {

        Activity activity = new FlexibleActivity("flex", "10:30", 7);

        assertEquals("flex",activity.getName());
        assertEquals(7,activity.getValue());


    }


}
