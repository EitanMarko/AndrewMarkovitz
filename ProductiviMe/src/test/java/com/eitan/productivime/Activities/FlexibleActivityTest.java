package com.eitan.productivime.Activities;
import com.eitan.productivime.BangForYourBuck.Activities.FlexibleActivity;
import com.eitan.productivime.BangForYourBuck.Time;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FlexibleActivityTest {

    @Test
    void simpleFlexActivity() {

        FlexibleActivity activity = new FlexibleActivity("flex", "10:30", 7);

        assertEquals("flex",activity.getName());
        assertEquals(7,activity.getValue());

    }

    @Test
    void invalidDuration() {

        // Duration must be a multiple of 5
        assertThrows(IllegalArgumentException.class, () -> {
            FlexibleActivity activity = new FlexibleActivity("flex", "10:32", 7);});

        // Valid - on 5-minute interval
        FlexibleActivity validActivity = new FlexibleActivity("flex", "10:30", 7);
        Time activityTime = new Time("10:30");
        assertEquals(activityTime.time,validActivity.getDuration());

        // Invalid change of duration - NOT on 5-minute interval
        assertThrows(IllegalArgumentException.class, () -> {
            validActivity.setDuration("2:03");});

        // Valid change of duration - on 5-minute interval
        validActivity.setDuration("2:05");
        Time newTime = new Time("2:05");
        assertEquals(newTime.time,validActivity.getDuration());



    }

    @Test
    void changePriorities() {

        FlexibleActivity activity = new FlexibleActivity("flex", "10:30", 7);
        assertEquals(7,activity.getValue());
        assertFalse(activity.isDoTodayActivity());

        assertThrows(IllegalArgumentException.class, () -> {
            activity.setValue(-1);}); // Cannot set a negative value
        assertThrows(IllegalArgumentException.class, () -> {
            activity.setValue(0);}); // Cannot set value below 1
        assertThrows(IllegalArgumentException.class, () -> {
            activity.setValue(11);}); // Cannot set value above 10

        activity.setValue(10);
        assertEquals(10,activity.getValue());
        assertFalse(activity.isDoTodayActivity());

        activity.doToday(); // sets value to 3000
        assertEquals(3000,activity.getValue());
        assertTrue(activity.isDoTodayActivity()); // This Activity has been set to a DoTodayActivity

        activity.makeFlexible(); // reverts value to most recently set 1-10 priority
        assertEquals(10,activity.getValue());
        assertFalse(activity.isDoTodayActivity()); // This Activity has been reverted to a normal FlexibleActivity

        activity.makeFlexible(); // doesn't change anything, because value is currently the most recently set 1-10 priority
        assertEquals(10,activity.getValue());
        assertFalse(activity.isDoTodayActivity());


    }
}
