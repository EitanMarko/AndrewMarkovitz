package com.eitan.productivime.Activities;
import com.eitan.productivime.BangForYourBuck.Interval;
import com.eitan.productivime.BangForYourBuck.Time;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class IntervalTest {

    @Test
    void invalidTimes() {

        // start >= end
        assertThrows(IllegalArgumentException.class, () -> {
            Interval interval = new Interval("9:00","8:00");});
        assertThrows(IllegalArgumentException.class, () -> {
            Interval interval = new Interval("9:00","9:00");});

        // Interval's start and end times must be on a 5-minute interval
        assertThrows(IllegalArgumentException.class, () -> {
            Interval interval = new Interval("9:01","10:00");});
        assertThrows(IllegalArgumentException.class, () -> {
            Interval interval = new Interval("9:00","10:01");});

    }

    @Test
    void newStart() {

        Interval interval = new Interval("7:00","8:00");
        Time seven = new Time("7:00");
        Time eight = new Time("8:00");
        assertEquals(seven.time, interval.getStart());
        assertEquals(eight.time, interval.getEnd());
        assertEquals(eight.time - seven.time, interval.getDuration());

        assertThrows(IllegalArgumentException.class, () -> {
            interval.setStart("8:05");}); // start is later than end ("8:05 > "8:00")
        assertThrows(IllegalArgumentException.class, () -> {
            interval.setStart("7:02");}); // start is NOT on a 5-minute interval

        // Change start time successfully
        interval.setStart("6:00");
        Time six = new Time("6:00");
        assertEquals(six.time, interval.getStart());
        assertEquals(eight.time, interval.getEnd());
        assertEquals(eight.time - six.time, interval.getDuration());
    }

    @Test
    void newEnd() {

        Interval interval = new Interval("7:00","8:00");
        Time seven = new Time("7:00");
        Time eight = new Time("8:00");
        assertEquals(seven.time, interval.getStart());
        assertEquals(eight.time, interval.getEnd());
        assertEquals(eight.time - seven.time, interval.getDuration());

        assertThrows(IllegalArgumentException.class, () -> {
            interval.setEnd("6:55");}); // end is earlier than start ("6:55 < "7:00")
        assertThrows(IllegalArgumentException.class, () -> {
            interval.setEnd("8:02");}); // end is NOT on a 5-minute interval

        // Change end time successfully
        interval.setEnd("9:00");
        Time nine = new Time("9:00");
        assertEquals(seven.time, interval.getStart());
        assertEquals(nine.time, interval.getEnd());
        assertEquals(nine.time - seven.time, interval.getDuration());
    }
}
