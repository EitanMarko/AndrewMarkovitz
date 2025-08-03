package com.eitan.productivime.Activities;

import com.eitan.productivime.BangForYourBuck.Activities.Activity;
import com.eitan.productivime.BangForYourBuck.Activities.SetActivity;
import com.eitan.productivime.BangForYourBuck.Interval;
import com.eitan.productivime.BangForYourBuck.Time;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SetActivityTest {

    @Test
    void simpleSetActivity() {

        Interval interval = new Interval("10:30","11:00");
        SetActivity activity = new SetActivity("set", "10:30","11:00");

        assertEquals("set",activity.getName());
        assertEquals(12,activity.getValue());

        Time tenThirty = new Time("10:30");
        Time eleven = new Time("11:00");
        assertEquals(tenThirty.time,activity.getStartTime());
        assertEquals(eleven.time,activity.getEndTime());
        assertEquals(eleven.time - tenThirty.time,activity.getDuration());
    }

    @Test
    void newStart() {

        SetActivity activity = new SetActivity("set", "10:30","11:00");

        Time tenThirty = new Time("10:30");
        Time eleven = new Time("11:00");
        assertEquals(tenThirty.time,activity.getStartTime());
        assertEquals(eleven.time,activity.getEndTime());
        assertEquals(eleven.time - tenThirty.time,activity.getDuration());

        // Interval is correct
        Interval interval = new Interval("10:30","11:00");
        assertEquals(interval.start,activity.getInterval().start);
        assertEquals(interval.end,activity.getInterval().end);

        assertThrows(IllegalArgumentException.class, () -> {
            activity.setStartTime("11:30");}); // start is later than end ("11:30 > "11:00")
        assertThrows(IllegalArgumentException.class, () -> {
            activity.setStartTime("10:32");}); // start is NOT on a 5-minute interval

        // Change start time successfully
        activity.setStartTime("10:00");
        Time ten = new Time("10:00");
        assertEquals(ten.time, activity.getStartTime());
        assertEquals(eleven.time, activity.getEndTime());
        assertEquals(eleven.time - ten.time, activity.getDuration());

        //Interval is changed accordingly
        interval.setStart("10:00");
        assertEquals(interval.start,activity.getInterval().start);
        assertEquals(interval.end,activity.getInterval().end);
    }

    @Test
    void newEnd() {

        SetActivity activity = new SetActivity("set", "10:30","11:00");

        Time tenThirty = new Time("10:30");
        Time eleven = new Time("11:00");
        assertEquals(tenThirty.time,activity.getStartTime());
        assertEquals(eleven.time,activity.getEndTime());
        assertEquals(eleven.time - tenThirty.time,activity.getDuration());

        // Interval is correct
        Interval interval = new Interval("10:30","11:00");
        assertEquals(interval.start,activity.getInterval().start);
        assertEquals(interval.end,activity.getInterval().end);

        assertThrows(IllegalArgumentException.class, () -> {
            activity.setEndTime("10:00");}); // end is earlier than start ("10:00 < "10:30")
        assertThrows(IllegalArgumentException.class, () -> {
            activity.setEndTime("11:02");}); // end is NOT on a 5-minute interval

        // Change start time successfully
        activity.setEndTime("12:00");
        Time twelve = new Time("12:00");
        assertEquals(tenThirty.time, activity.getStartTime());
        assertEquals(twelve.time, activity.getEndTime());
        assertEquals(twelve.time - tenThirty.time, activity.getDuration());

        //Interval is changed accordingly
        interval.setEnd("12:00");
        assertEquals(interval.start,activity.getInterval().start);
        assertEquals(interval.end,activity.getInterval().end);

        
    }
}
